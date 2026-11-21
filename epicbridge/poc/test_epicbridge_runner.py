"""Focused regression tests for Legendary library aggregation."""
from __future__ import annotations

import json
import os
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import epicbridge_runner as runner


def game(app_name, title, catalog="catalog-main", *, is_dlc=False):
    return SimpleNamespace(
        app_name=app_name,
        app_title=title,
        catalog_item_id=catalog,
        is_dlc=is_dlc,
        third_party_store=None,
        app_version=lambda platform: "1.0",
    )


class LibraryMergeTests(unittest.TestCase):
    def run_library(self, games, dlcs, extra_games, extra_dlcs):
        class FakeCore:
            def login(self):
                return True

            def get_game_and_dlc_list(self, platform):
                if platform != "Windows":
                    raise AssertionError(f"unexpected platform: {platform}")
                return list(games), {key: list(value) for key, value in dlcs.items()}

            def get_non_asset_library_items(self):
                return list(extra_games), {
                    key: list(value) for key, value in extra_dlcs.items()
                }

        with patch.object(runner, "_require_login"):
            return runner._library(FakeCore())

    def test_non_asset_catalog_is_added_without_key_error(self):
        base = game("BASE", "Base Game")
        extra_base = game("EXTRA", "Extra Game", "catalog-new")
        extra_dlc = game("BONUS", "Bonus Content", "catalog-new", is_dlc=True)
        result = self.run_library([base], {}, [extra_base], {"catalog-new": [extra_dlc]})
        self.assertEqual(len(result), 2)
        self.assertEqual([item["app_name"] for item in result[1]["dlcs"]], ["BONUS"])

    def test_duplicate_non_asset_games_are_removed(self):
        base = game("BASE", "Base Game")
        extra = game("EXTRA", "Extra Game", "catalog-extra")
        result = self.run_library([base], {}, [extra, extra], {})
        self.assertEqual([item["app_name"] for item in result], ["BASE", "EXTRA"])

    def test_existing_primary_game_is_not_duplicated(self):
        base = game("BASE", "Base Game")
        result = self.run_library([base], {}, [base], {})
        self.assertEqual([item["app_name"] for item in result], ["BASE"])

    def test_games_are_sorted_case_insensitively(self):
        games = [
            game("ZED", "zeta"),
            game("ALPHA", "Alpha"),
            game("BETA", "beta"),
        ]
        result = self.run_library(games, {}, [], {})
        self.assertEqual([item["title"] for item in result], ["Alpha", "beta", "zeta"])

    def test_primary_and_non_asset_dlcs_are_merged(self):
        base = game("BASE", "Base Game")
        primary = game("DLC_A", "A", "catalog-main", is_dlc=True)
        secondary = game("DLC_B", "B", "catalog-main", is_dlc=True)
        result = self.run_library([base], {"catalog-main": [primary]}, [],
                                  {"catalog-main": [secondary]})
        self.assertEqual(
            [item["app_name"] for item in result[0]["dlcs"]],
            ["DLC_A", "DLC_B"],
        )


class InstallJobControlTests(unittest.TestCase):
    def test_status_snapshot_is_atomic_and_contains_only_supported_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "status.json")
            runner._write_job_status(
                path, "running", phase="downloading", progress=42.5,
                download_speed=1234, private_path="/must/not/be/serialized",
            )
            with open(path, encoding="utf-8") as source:
                snapshot = json.load(source)
            self.assertEqual(snapshot["state"], "running")
            self.assertEqual(snapshot["phase"], "downloading")
            self.assertEqual(snapshot["progress"], 42.5)
            self.assertEqual(snapshot["download_speed"], 1234.0)
            self.assertNotIn("private_path", snapshot)
            self.assertFalse(os.path.exists(path + ".tmp"))

    def test_legendary_status_queue_is_connected_and_progress_is_published(self):
        class FakeDownloader:
            def __init__(self):
                self.alive = False
                self.status_queue = None
                self.error = None

            def start(self):
                self.alive = True
                self.status_queue.put(SimpleNamespace(
                    progress=42.5, download_speed=1200,
                    write_speed=300, read_speed=400,
                ))

            def is_alive(self):
                return self.alive

            def join(self, timeout=None):
                self.alive = False

        downloader = FakeDownloader()
        fake_game = SimpleNamespace(
            app_name="TEST", third_party_store=None, title="Test",
            install_path="/games/Test", executable="test.exe", version="1",
        )
        conditions = SimpleNamespace(failures=[], warnings=[])
        received = {}

        def prepare_download(*args, **kwargs):
            received.update(kwargs)
            downloader.status_queue = kwargs["status_q"]
            return downloader, object(), fake_game

        core = SimpleNamespace(
            login=lambda: True,
            get_game=lambda *args, **kwargs: fake_game,
            prepare_download=prepare_download,
            check_installation_conditions=lambda *args: conditions,
            install_game=lambda *args: {},
        )
        with tempfile.TemporaryDirectory() as directory:
            control = os.path.join(directory, "control.json")
            status = os.path.join(directory, "status.json")
            with patch.object(runner, "_write_job_status",
                              wraps=runner._write_job_status) as write_status:
                runner._install(core, {"app_name": "TEST", "base_path": "/games",
                                       "job_control_path": control,
                                       "job_status_path": status})
            self.assertIsNotNone(received.get("status_q"))
            self.assertTrue(any(
                call.kwargs.get("progress") == 42.5
                and call.kwargs.get("download_speed") == 1200
                for call in write_status.call_args_list
            ))

    def test_cancel_before_downloader_start_skips_thread_creation(self):
        class FakeDownloader:
            started = False
            def start(self):
                self.started = True
            def is_alive(self):
                return False

        downloader = FakeDownloader()
        fake_game = SimpleNamespace(app_name="TEST", third_party_store=None, title="Test")
        conditions = SimpleNamespace(failures=[], warnings=[])
        core = SimpleNamespace(
            login=lambda: True,
            get_game=lambda *args, **kwargs: fake_game,
            prepare_download=lambda *args, **kwargs: (downloader, object(), fake_game),
            check_installation_conditions=lambda *args: conditions,
            install_game=lambda *args: self.fail("install_game must not run"),
        )
        with tempfile.TemporaryDirectory() as directory:
            control = os.path.join(directory, "control.json")
            status = os.path.join(directory, "status.json")
            with open(control, "w", encoding="utf-8") as output:
                json.dump({"cancel": True}, output)
            result = runner._install(core, {
                "app_name": "TEST", "base_path": "/games",
                "job_control_path": control, "job_status_path": status,
            })
            self.assertTrue(result["cancelled"])
            self.assertFalse(downloader.started)
            with open(status, encoding="utf-8") as source:
                self.assertEqual(json.load(source)["state"], "cancelled")

    def test_cancel_arriving_as_downloader_exits_skips_install_game(self):
        class FakeDownloader:
            def start(self):
                pass
            def is_alive(self):
                return False
            def join(self, timeout=None):
                pass

        downloader = FakeDownloader()
        install_game_called = []
        fake_game = SimpleNamespace(app_name="TEST", third_party_store=None, title="Test")
        conditions = SimpleNamespace(failures=[], warnings=[])
        core = SimpleNamespace(
            login=lambda: True,
            get_game=lambda *args, **kwargs: fake_game,
            prepare_download=lambda *args, **kwargs: (downloader, object(), fake_game),
            check_installation_conditions=lambda *args: conditions,
            install_game=lambda *args: install_game_called.append(True),
        )
        # First poll (pre-start) is false; final post-worker poll sees cancellation.
        with tempfile.TemporaryDirectory() as directory:
            status = os.path.join(directory, "status.json")
            with patch.object(runner, "_cancel_requested", side_effect=[False, True]):
                result = runner._install(core, {
                    "app_name": "TEST", "base_path": "/games",
                    "job_control_path": os.path.join(directory, "control.json"),
                    "job_status_path": status,
                })
            self.assertTrue(result["cancelled"])
            self.assertEqual(install_game_called, [])
            with open(status, encoding="utf-8") as source:
                self.assertEqual(json.load(source)["state"], "cancelled")

    def test_cancelled_install_stops_downloader_and_skips_install_game(self):
        class FakeDownloader:
            def __init__(self):
                self.alive = False
                self.stop_called = False
                self.error = None

            def start(self):
                self.alive = True

            def is_alive(self):
                return self.alive

            def stop(self):
                self.stop_called = True
                self.alive = False

            def join(self, timeout=None):
                return None

        downloader = FakeDownloader()
        install_game_called = []
        fake_game = SimpleNamespace(
            app_name="TEST", third_party_store=None, title="Test",
            install_path="/games/Test", executable="test.exe", version="1",
        )
        conditions = SimpleNamespace(failures=[], warnings=[])
        core = SimpleNamespace(
            login=lambda: True,
            get_game=lambda *args, **kwargs: fake_game,
            prepare_download=lambda *args, **kwargs: (downloader, object(), fake_game),
            check_installation_conditions=lambda *args: conditions,
            install_game=lambda *args: install_game_called.append(True),
        )
        with tempfile.TemporaryDirectory() as directory:
            control = os.path.join(directory, "control.json")
            status = os.path.join(directory, "status.json")
            # Initial, post-metadata, and post-prepare gates are clear; the
            # next poll simulates cancellation after downloader start.
            with patch.object(runner, "_cancel_requested", side_effect=[False, False, False, True]):
                result = runner._install(core, {
                    "app_name": "TEST", "base_path": "/games",
                    "job_control_path": control, "job_status_path": status,
                })
            self.assertTrue(result["cancelled"])
            self.assertTrue(downloader.stop_called)
            self.assertEqual(install_game_called, [])
            with open(status, encoding="utf-8") as source:
                snapshot = json.load(source)
            self.assertEqual(snapshot["state"], "cancelled")
            self.assertEqual(snapshot["phase"], "cancelled")


class ErrorSanitizationTests(unittest.TestCase):
    def test_command_failure_does_not_return_exception_message_or_traceback(self):
        secret_path = "/storage/emulated/0/MaliWinlator/private-install/token.dat"

        def fail_core():
            raise RuntimeError(f"download failed at {secret_path}")

        with patch.object(runner, "_core", side_effect=fail_core):
            response = runner.handle("installed", "{}")

        self.assertIn('"error":"EPIC_OPERATION_FAILED"', response)
        self.assertIn("Epic operation failed", response)
        self.assertNotIn(secret_path, response)
        self.assertNotIn("RuntimeError", response)
        self.assertNotIn("Traceback", response)

    def test_invalid_payload_does_not_echo_sensitive_payload_content(self):
        secret_path = "/data/user/0/com.maliwinlator.epicbridge/files/private"

        response = runner.handle("installed", '{"secret":"' + secret_path)

        self.assertIn('"error":"EPIC_OPERATION_FAILED"', response)
        self.assertNotIn(secret_path, response)


if __name__ == "__main__":
    unittest.main(verbosity=2)
