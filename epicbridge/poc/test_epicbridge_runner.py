"""Focused regression tests for Legendary library aggregation."""
from __future__ import annotations

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

        response = runner.handle("installed", '{"secret":"' + secret_path + '"}')

        self.assertIn('"error":"EPIC_OPERATION_FAILED"', response)
        self.assertNotIn(secret_path, response)


if __name__ == "__main__":
    unittest.main(verbosity=2)
