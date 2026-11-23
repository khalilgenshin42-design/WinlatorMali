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

    def test_job_lock_excludes_other_threads_until_released(self):
        import threading
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "job.lock")
            entered = threading.Event()
            acquired = threading.Event()

            def contender():
                entered.set()
                with runner._exclusive_job_lock(path):
                    acquired.set()

            with runner._exclusive_job_lock(path):
                thread = threading.Thread(target=contender)
                thread.start()
                self.assertTrue(entered.wait(2))
                self.assertFalse(acquired.wait(0.1))
            thread.join(2)
            self.assertFalse(thread.is_alive())
            self.assertTrue(acquired.is_set())

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
            lock = os.path.join(directory, "job.lock")
            with patch.object(runner, "_write_job_status",
                              wraps=runner._write_job_status) as write_status:
                runner._install(core, {"app_name": "TEST", "base_path": "/games",
                                       "job_control_path": control,
                                       "job_status_path": status, "job_lock_path": lock})
            self.assertIsNotNone(received.get("status_q"))
            self.assertEqual(received.get("max_workers"), 2)
            self.assertEqual(received.get("max_shm"), 256)
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
            lock = os.path.join(directory, "job.lock")
            with open(control, "w", encoding="utf-8") as output:
                json.dump({"cancel": True}, output)
            result = runner._install(core, {
                "app_name": "TEST", "base_path": "/games",
                "job_control_path": control, "job_status_path": status,
                    "job_lock_path": lock,
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
            lock = os.path.join(directory, "job.lock")
            with patch.object(runner, "_cancel_requested", side_effect=[False, True]):
                result = runner._install(core, {
                    "app_name": "TEST", "base_path": "/games",
                    "job_control_path": os.path.join(directory, "control.json"),
                    "job_status_path": status,
                    "job_lock_path": lock,
                })
            self.assertTrue(result["cancelled"])
            self.assertEqual(install_game_called, [])
            with open(status, encoding="utf-8") as source:
                self.assertEqual(json.load(source)["state"], "cancelled")

    def test_cancelled_install_stops_downloader_and_skips_install_game(self):
        class FakeDownloader:
            def __init__(self):
                self.alive = False
                self.stop_calls = 0
                self.join_calls = 0
                self.error = None

            def start(self):
                self.alive = True

            def is_alive(self):
                return self.alive

            def stop(self):
                self.stop_calls += 1

            def join(self, timeout=None):
                self.join_calls += 1
                if self.join_calls >= 3:
                    self.alive = False

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
            lock = os.path.join(directory, "job.lock")
            # Three early gates pass; cancellation remains asserted while the
            # worker takes multiple join cycles to exit.
            cancel_polls = [False, False, False, True] + [True] * 8
            with patch.object(runner, "_cancel_requested", side_effect=cancel_polls):
                result = runner._install(core, {
                    "app_name": "TEST", "base_path": "/games",
                    "job_control_path": control, "job_status_path": status,
                    "job_lock_path": lock,
                })
            self.assertTrue(result["cancelled"])
            self.assertEqual(downloader.stop_calls, 1)
            self.assertEqual(downloader.join_calls, 3)
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


class GameDetailsTests(unittest.TestCase):
    def run_details(self, game, remote=None, achievements=None):
        egs = SimpleNamespace(
            get_user_cloud_saves=lambda app_name: remote or {"files": []},
        )
        core = SimpleNamespace(
            login=lambda: True,
            get_game=lambda *args, **kwargs: game,
            get_achievements=lambda selected, update=False: achievements,
            egs=egs,
        )
        with patch.object(runner, "_require_login"):
            return runner._game_details(core, {"app_name": "TEST"})

    def test_game_details_uses_cached_metadata_without_catalog_refresh(self):
        game = SimpleNamespace(
            app_name="TEST", app_title="Test", namespace="test-ns",
            supports_cloud_saves=False,
        )
        calls = {}
        def get_game(*args, **kwargs):
            calls.update(kwargs)
            return game
        core = SimpleNamespace(
            get_game=get_game,
            get_achievements=lambda selected, update=False: None,
            egs=SimpleNamespace(),
        )
        with patch.object(runner, "_require_login"):
            runner._game_details(core, {"app_name": "TEST"})
        self.assertIs(calls.get("update_meta"), False)
        self.assertEqual(calls.get("platform"), "Windows")

    def test_achievement_summary_is_normalized_and_hidden_text_is_redacted(self):
        game = SimpleNamespace(
            app_name="TEST", app_title="Test", namespace="test-ns",
            supports_cloud_saves=False,
        )
        raw = {
            "total_achievements": 2, "total_product_xp": 100,
            "user_unlocked": 1, "user_xp": 50,
            "completed": [{
                "name": "FIRST", "display_name": "First Steps",
                "description": "Complete the tutorial", "unlocked": True,
                "progress": 1.0, "xp": 50, "icon_link": "https://cdn.example/icon.png",
            }],
            "hidden": [{
                "name": "SECRET", "display_name": "Secret Ending",
                "description": "Spoiler text", "hidden": True,
                "unlocked": False, "progress": 0.0, "xp": 50,
                "icon_link": "https://cdn.example/secret.png",
            }],
        }
        result = self.run_details(game, achievements=raw)["achievements"]
        self.assertTrue(result["available"])
        self.assertEqual(result["total_achievements"], 2)
        self.assertEqual(result["user_unlocked"], 1)
        self.assertEqual(result["items"][0]["display_name"], "First Steps")
        self.assertTrue(result["items"][0]["unlocked"])
        self.assertEqual(result["items"][1]["display_name"], "Hidden achievement")
        self.assertEqual(result["items"][1]["description"], "")
        self.assertEqual(result["items"][1]["icon_link"], "")

    def test_achievement_summary_caps_item_count(self):
        game = SimpleNamespace(
            app_name="TEST", app_title="Test", namespace="test-ns",
            supports_cloud_saves=False,
        )
        entries = [
            {"name": f"ACH_{index}", "display_name": f"Achievement {index}",
             "unlocked": False, "progress": 0.0, "xp": 1}
            for index in range(125)
        ]
        result = self.run_details(
            game, achievements={"uninitiated": entries}
        )["achievements"]
        self.assertEqual(len(result["items"]), 100)
        self.assertTrue(result["truncated"])

    def test_missing_achievement_summary_is_not_reported_as_available(self):
        game = SimpleNamespace(
            app_name="TEST", app_title="Test", namespace="test-ns",
            supports_cloud_saves=False,
        )
        result = self.run_details(game, achievements=None)["achievements"]
        self.assertFalse(result["available"])

    def test_malformed_achievement_metadata_is_safely_normalized(self):
        game = SimpleNamespace(
            app_name="TEST", app_title="Test", namespace="test-ns",
            supports_cloud_saves=False,
        )
        raw = {
            "total_achievements": "not-a-number",
            "total_product_xp": None,
            "user_unlocked": -4,
            "user_xp": "unknown",
            "uninitiated": [{
                "name": None, "display_name": 42, "description": None,
                "unlocked": False, "progress": float("nan"),
                "xp": "bad", "icon_link": "javascript:alert(1)",
                "tier": None, "rarity": 12,
            }],
        }
        result = self.run_details(game, achievements=raw)["achievements"]
        self.assertTrue(result["available"])
        self.assertEqual(result["total_achievements"], 0)
        self.assertEqual(result["user_unlocked"], 0)
        self.assertEqual(result["items"][0]["name"], "")
        self.assertEqual(result["items"][0]["display_name"], "")
        self.assertEqual(result["items"][0]["progress"], 0.0)
        self.assertEqual(result["items"][0]["xp"], 0)
        self.assertEqual(result["items"][0]["icon_link"], "")
        self.assertEqual(result["items"][0]["tier"], "")
        self.assertEqual(result["items"][0]["rarity"], "")

    def test_windows_cloud_save_flag_controls_support(self):
        game = SimpleNamespace(
            app_name="TEST", app_title="Test", namespace="test-ns",
            supports_cloud_saves=False, supports_mac_cloud_saves=True,
        )
        result = self.run_details(game, {"files": {"save-a": {}, "save-b": {}}})
        self.assertFalse(result["cloud_saves"]["supported"])
        self.assertIsNone(result["cloud_saves"]["remote_available"])
        self.assertIsNone(result["cloud_saves"]["remote_count"])

    def test_remote_cloud_save_dictionary_is_counted(self):
        game = SimpleNamespace(
            app_name="TEST", app_title="Test", namespace="test-ns",
            supports_cloud_saves=True,
        )
        result = self.run_details(game, {"files": {"save-a": {}, "save-b": {}}})
        self.assertTrue(result["cloud_saves"]["supported"])
        self.assertTrue(result["cloud_saves"]["remote_available"])
        self.assertEqual(result["cloud_saves"]["remote_count"], 2)

    def test_remote_cloud_save_list_is_counted(self):
        game = SimpleNamespace(
            app_name="TEST", app_title="Test", namespace="test-ns",
            supports_cloud_saves=True,
        )
        result = self.run_details(game, {"files": ["save-a", "save-b", "save-c"]})
        self.assertEqual(result["cloud_saves"]["remote_count"], 3)

    def test_game_details_command_dispatches_to_details_handler(self):
        fake = {"app_name": "TEST"}
        with patch.object(runner, "_core", return_value=object()), \
             patch.object(runner, "_game_details", return_value=fake) as details:
            response = json.loads(runner.handle("game_details", '{"app_name":"TEST"}'))
        details.assert_called_once()
        self.assertTrue(response["ok"])
        self.assertEqual(response["game"], fake)

    def test_library_command_still_dispatches_to_library_handler(self):
        fake = [{"app_name": "TEST"}]
        with patch.object(runner, "_core", return_value=SimpleNamespace(lgd=SimpleNamespace(userdata={}))), patch.object(runner, "_library", return_value=fake):
            response = json.loads(runner.handle("library", "{}"))
        self.assertTrue(response["ok"])
        self.assertEqual(response["games"], fake)

    def test_app_name_rejects_embedded_nul(self):
        core = SimpleNamespace(login=lambda: True)
        with patch.object(runner, "_require_login"):
            with self.assertRaises(ValueError):
                runner._game_details(core, {"app_name": "BAD\x00NAME"})


if __name__ == "__main__":
    unittest.main(verbosity=2)
