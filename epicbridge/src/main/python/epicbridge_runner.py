"""Structured Android service API over pinned Legendary 0.21.1."""
from __future__ import annotations

import dataclasses
import json
import logging
import os
import traceback

log = logging.getLogger("EpicBridge")


def _dump(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), default=str)


def _core():
    from legendary.core import LegendaryCore
    return LegendaryCore()


def _account(core):
    data = getattr(core.lgd, "userdata", {}) or {}
    return {
        "display_name": data.get("displayName", ""),
        "account_id": data.get("account_id", ""),
    }


def _require_login(core):
    if not core.login():
        raise RuntimeError("Epic authentication is required or the saved session expired")


def _library(core):
    _require_login(core)
    games, dlc_by_catalog = core.get_game_and_dlc_list(platform="Windows")
    non_asset_games, non_asset_dlc = core.get_non_asset_library_items()
    known = {game.app_name for game in games}
    games.extend(game for game in non_asset_games if game.app_name not in known)
    for catalog_id, dlcs in non_asset_dlc.items():
        dlc_by_catalog[catalog_id].extend(dlcs)
    result = []
    for game in sorted(games, key=lambda item: item.app_title.casefold()):
        dlcs = []
        for dlc in dlc_by_catalog.get(game.catalog_item_id, []):
            dlcs.append({
                "app_name": dlc.app_name,
                "title": dlc.app_title,
                "version": dlc.app_version("Windows") or "",
                "is_dlc": True,
            })
        result.append({
            "app_name": game.app_name,
            "title": game.app_title,
            "version": game.app_version("Windows") or "",
            "is_dlc": bool(game.is_dlc),
            "third_party_store": game.third_party_store or "",
            "dlcs": dlcs,
        })
    return result


def _installed(core):
    games = core.get_installed_list(include_dlc=True)
    return [{
        "app_name": game.app_name,
        "title": game.title,
        "version": game.version,
        "install_path": game.install_path,
        "executable": game.executable,
        "working_directory": os.path.dirname(os.path.join(game.install_path, game.executable)),
        "platform": game.platform,
        "is_dlc": bool(game.is_dlc),
        "install_size": int(game.install_size or 0),
    } for game in sorted(games, key=lambda item: item.title.casefold())]


def _install(core, payload):
    _require_login(core)
    app_name = str(payload.get("app_name", "")).strip()
    base_path = str(payload.get("base_path", "")).strip()
    if not app_name or not base_path:
        raise ValueError("app_name and base_path are required")
    if not os.path.isabs(base_path):
        raise ValueError("base_path must be an absolute shared-storage path")

    game = core.get_game(app_name, update_meta=True, platform="Windows")
    if not game:
        raise ValueError("This title is not available in the Windows Epic library")
    if game.third_party_store:
        raise ValueError("This title requires a third-party store and cannot be installed by Legendary")

    dlm, analysis, installed_game = core.prepare_download(
        game,
        base_path=base_path,
        platform="Windows",
        max_workers=max(1, min(int(payload.get("max_workers", 4)), 4)),
        max_shm=max(128, min(int(payload.get("max_shared_memory_mib", 512)), 512)),
        dl_timeout=30,
    )
    conditions = core.check_installation_conditions(analysis, installed_game, game)
    if conditions.failures:
        raise RuntimeError("; ".join(sorted(conditions.failures)))

    dlm.start()
    dlm.join()
    if dlm.is_alive():
        raise RuntimeError("Legendary download worker did not stop")
    worker_error = getattr(dlm, "error", None)
    if worker_error:
        raise RuntimeError(f"Legendary download failed: {worker_error}")
    core.install_game(installed_game)
    return {
        "app_name": installed_game.app_name,
        "title": installed_game.title,
        "install_path": installed_game.install_path,
        "executable": installed_game.executable,
        "version": installed_game.version,
        "warnings": sorted(conditions.warnings),
    }


def _launch_info(core, payload):
    _require_login(core)
    app_name = str(payload.get("app_name", "")).strip()
    if not app_name:
        raise ValueError("app_name is required")
    if not core.is_installed(app_name):
        raise ValueError("The selected Epic game is not installed")
    params = core.get_launch_parameters(app_name, disable_wine=True)
    return {
        "app_name": app_name,
        "game_executable": params.game_executable,
        "game_directory": params.game_directory,
        "working_directory": params.working_directory,
        "game_parameters": list(params.game_parameters),
        "egl_parameters": list(params.egl_parameters),
        "user_parameters": list(params.user_parameters),
        "environment": dict(params.environment or {}),
    }


def handle(command: str, payload_json: str = "{}") -> str:
    try:
        payload = json.loads(payload_json or "{}")
        if not isinstance(payload, dict):
            raise ValueError("payload must be a JSON object")

        if command == "begin_login":
            from legendary.api.egs import EPCAPI
            return _dump({"ok": True, "authorization_url": EPCAPI().get_auth_url()})

        core = _core()
        if command == "finish_login":
            code = str(payload.get("authorization_code", "")).strip()
            if not code:
                raise ValueError("authorization_code is required")
            if not core.auth_code(code) or not core.login():
                raise RuntimeError("Epic rejected the authorization code")
            return _dump({"ok": True, "account": _account(core)})

        if command == "status":
            try:
                logged_in = bool(core.login())
            except Exception:
                logged_in = False
            return _dump({
                "ok": True,
                "logged_in": logged_in,
                "account": _account(core) if logged_in else {},
            })

        if command == "logout":
            core.lgd.invalidate_userdata()
            return _dump({"ok": True, "logged_in": False})

        if command == "library":
            return _dump({"ok": True, "games": _library(core), "account": _account(core)})

        if command == "installed":
            return _dump({"ok": True, "games": _installed(core)})

        if command == "install":
            result = _install(core, payload)
            return _dump({"ok": True, "game": result})

        if command == "uninstall":
            _require_login(core)
            app_name = str(payload.get("app_name", "")).strip()
            game = core.get_installed_game(app_name)
            if not game:
                raise ValueError("The selected game is not installed")
            core.uninstall_game(game, delete_files=True)
            return _dump({"ok": True, "app_name": app_name})

        if command == "launch_info":
            return _dump({"ok": True, "launch": _launch_info(core, payload)})

        raise ValueError(f"Unsupported Epic Bridge command: {command}")
    except Exception as exc:
        log.exception("Epic Bridge command failed: %s", command)
        return _dump({
            "ok": False,
            "error": type(exc).__name__,
            "message": str(exc),
            "trace": traceback.format_exc(limit=4),
        })
