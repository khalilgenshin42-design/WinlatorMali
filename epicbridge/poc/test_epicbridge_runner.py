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
        extra_dlc = game("BONUS", "Bonus Content", "catalog-new", is_dlc=True)
        result = self.run_library([base], {}, [], {"catalog-new": [extra_dlc]})
        self.assertEqual(len(result), 1)
        self.assertEqual([item["app_name"] for item in result[0]["dlcs"]], ["BONUS"])

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


if __name__ == "__main__":
    unittest.main(verbosity=2)
