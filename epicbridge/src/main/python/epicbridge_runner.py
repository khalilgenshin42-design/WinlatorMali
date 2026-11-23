"""Structured Android service API over pinned Legendary 0.21.1."""
from __future__ import annotations

import base64
import fcntl
import uuid
from contextlib import contextmanager
import json
import logging
import os
from queue import Queue
from urllib.parse import urlsplit

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


def _cover_image(game):
    """Return one HTTPS Epic catalog cover URL without exposing full catalog metadata."""
    metadata = getattr(game, "metadata", None)
    if not isinstance(metadata, dict):
        return ""
    images = metadata.get("keyImages", [])
    if not isinstance(images, list):
        return ""

    by_type = {}
    for image in images:
        if isinstance(image, dict):
            image_type = image.get("type")
            if isinstance(image_type, str) and image_type not in by_type:
                by_type[image_type] = image

    # Prefer portrait box art, then wide offer art, then smaller catalog previews.
    for image_type in (
        "DieselGameBoxTall",
        "OfferImageTall",
        "OfferImageWide",
        "DieselGameBox",
        "Thumbnail",
        "Featured",
    ):
        image = by_type.get(image_type, {})
        url = image.get("url", "")
        if isinstance(url, str) and url.startswith("https://") and len(url) <= 2048:
            return url

    # Catalog variants can introduce new image types; use the first safe HTTPS image.
    for image in images:
        if isinstance(image, dict):
            url = image.get("url", "")
            if isinstance(url, str) and url.startswith("https://") and len(url) <= 2048:
                return url
    return ""


def _library(core):
    _require_login(core)
    games, dlc_by_catalog = core.get_game_and_dlc_list(platform="Windows")
    non_asset_games, non_asset_dlc = core.get_non_asset_library_items()
    known = {game.app_name for game in games}
    for game in non_asset_games:
        if game.app_name not in known:
            games.append(game)
            known.add(game.app_name)
    for catalog_id, dlcs in non_asset_dlc.items():
        # Non-asset library entries can introduce a catalog that was absent
        # from the primary asset catalog map.
        dlc_by_catalog.setdefault(catalog_id, []).extend(dlcs)
    result = []
    for game in sorted(games, key=lambda item: (item.app_title or item.app_name).casefold()):
        dlcs = []
        for dlc in dlc_by_catalog.get(game.catalog_item_id, []):
            dlcs.append({
                "app_name": dlc.app_name,
                "title": dlc.app_title or dlc.app_name,
                "version": dlc.app_version("Windows") or "",
                "is_dlc": True,
            })
        result.append({
            "app_name": game.app_name,
            "title": game.app_title or game.app_name,
            "version": game.app_version("Windows") or "",
            "is_dlc": bool(game.is_dlc),
            "third_party_store": game.third_party_store or "",
            "cover_url": _cover_image(game),
            "dlcs": dlcs,
        })
    return result



def _game_details(core, payload):
    """Read-only, bounded Epic account metadata for one owned Windows title."""
    _require_login(core)
    app_name = str(payload.get("app_name", "")).strip()
    if not app_name or len(app_name) > 128 or any(ch in app_name for ch in ("/", "\\", "\x00")):
        raise ValueError("A valid Epic app_name is required")
    game = core.get_game(app_name, update_meta=True, platform="Windows")
    if not game:
        raise ValueError("The selected Epic title could not be found")
    namespace = str(getattr(game, "namespace", "") or "").strip()
    metadata = getattr(game, "metadata", None)
    if not namespace and isinstance(metadata, dict):
        namespace = str(metadata.get("namespace", "") or "").strip()
    egs = getattr(core, "egs", None)
    result = {
        "app_name": app_name,
        "title": str(getattr(game, "app_title", "") or app_name)[:256],
        "achievements": {"available": False, "definitions": None, "user_state": None},
        "cloud_saves": {
            "supported": bool(getattr(game, "supports_cloud_saves", False)),
            "remote_available": None,
            "remote_count": None,
        },
        "playtime": {"available": False},
    }
    if namespace and callable(getattr(core, "get_achievements", None)):
        try:
            # Use Legendary's own 0.21.1 model/response parser rather than
            # exposing raw GraphQL payloads or guessing their nested schema.
            summary = core.get_achievements(game, update=True)
            if isinstance(summary, dict):
                def safe_int(value):
                    try:
                        return max(0, min(int(value or 0), 2_147_483_647))
                    except (TypeError, ValueError, OverflowError):
                        return 0

                def safe_text(value, limit):
                    return value[:limit] if isinstance(value, str) else ""

                normalized = {
                    "available": True,
                    "total_achievements": safe_int(summary.get("total_achievements")),
                    "total_product_xp": safe_int(summary.get("total_product_xp")),
                    "user_unlocked": safe_int(summary.get("user_unlocked")),
                    "user_xp": safe_int(summary.get("user_xp")),
                    "items": [],
                    "truncated": False,
                }
                for category in ("completed", "in_progress", "uninitiated", "hidden"):
                    entries = summary.get(category, [])
                    if not isinstance(entries, list):
                        continue
                    for entry in entries:
                        if not isinstance(entry, dict):
                            continue
                        if len(normalized["items"]) >= 100:
                            normalized["truncated"] = True
                            break
                        unlocked = bool(entry.get("unlocked", False))
                        hidden = bool(entry.get("hidden", False)) and not unlocked
                        try:
                            progress = float(entry.get("progress", 0.0) or 0.0)
                        except (TypeError, ValueError):
                            progress = 0.0
                        if not 0.0 <= progress <= 1.0:
                            progress = 0.0
                        try:
                            xp = max(0, int(entry.get("xp", 0) or 0))
                        except (TypeError, ValueError):
                            xp = 0
                        icon_link = safe_text(entry.get("icon_link"), 2048)
                        try:
                            parsed_icon = urlsplit(icon_link)
                            if parsed_icon.scheme != "https" or not parsed_icon.hostname:
                                icon_link = ""
                        except ValueError:
                            icon_link = ""
                        normalized["items"].append({
                            "name": safe_text(entry.get("name"), 128),
                            "display_name": "Hidden achievement" if hidden else safe_text(entry.get("display_name"), 160),
                            "description": "" if hidden else safe_text(entry.get("description"), 512),
                            "unlocked": unlocked,
                            "hidden": hidden,
                            "progress": progress,
                            "xp": xp,
                            "tier": safe_text(entry.get("tier"), 32),
                            "rarity": safe_text(entry.get("rarity"), 32),
                            "unlock_date": safe_text(str(entry.get("unlock_date")) if entry.get("unlock_date") is not None else "", 64),
                            "icon_link": "" if hidden else icon_link,
                            "category": category,
                        })
                if len(_dump(normalized).encode("utf-8")) <= 48 * 1024:
                    result["achievements"] = normalized
                else:
                    result["achievements"] = {
                        "available": True,
                        "total_achievements": normalized["total_achievements"],
                        "total_product_xp": normalized["total_product_xp"],
                        "user_unlocked": normalized["user_unlocked"],
                        "user_xp": normalized["user_xp"],
                        "items": [],
                        "truncated": True,
                    }
        except Exception as exc:
            log.warning("Achievement query unavailable (type=%s)", type(exc).__name__)
            result["achievements"]["error"] = "ACHIEVEMENTS_UNAVAILABLE"
    if result["cloud_saves"]["supported"] and egs is not None:
        try:
            remote = egs.get_user_cloud_saves(app_name)
            files = remote.get("files", []) if isinstance(remote, dict) else []
            if isinstance(files, (list, dict)):
                result["cloud_saves"]["remote_available"] = bool(files)
                result["cloud_saves"]["remote_count"] = len(files)
            else:
                result["cloud_saves"]["remote_available"] = False
                result["cloud_saves"]["remote_count"] = 0
        except Exception as exc:
            log.warning("Cloud-save metadata unavailable (type=%s)", type(exc).__name__)
            result["cloud_saves"]["error"] = "CLOUD_METADATA_UNAVAILABLE"
    return result


def _installed(core):
    games = core.get_installed_list(include_dlc=True)
    result = []
    for game in sorted(games, key=lambda item: (item.title or item.app_name).casefold()):
        executable = game.executable or ""
        install_path = game.install_path or ""
        result.append({
            "app_name": game.app_name,
            "title": game.title or game.app_name,
            "version": game.version or "",
            "install_path": install_path,
            "executable": executable,
            "working_directory": (
                os.path.dirname(os.path.join(install_path, executable))
                if executable else install_path
            ),
            "platform": game.platform,
            "is_dlc": bool(game.is_dlc),
            "install_size": int(game.install_size or 0),
        })
    return result


def _write_job_status(path, state, **fields):
    """Atomically publish a sanitized job snapshot for the Android provider."""
    if not path:
        return
    snapshot = {"state": state}
    phase = fields.get("phase")
    if isinstance(phase, str) and phase in ("preparing", "downloading", "stopping", "cancelled", "installing"):
        snapshot["phase"] = phase
    for key in ("progress", "download_speed", "write_speed", "read_speed"):
        value = fields.get(key)
        if isinstance(value, (int, float)) and value >= 0:
            snapshot[key] = min(float(value), 100.0) if key == "progress" else float(value)
    temporary = path + ".tmp"
    try:
        with open(temporary, "w", encoding="utf-8") as output:
            json.dump(snapshot, output, separators=(",", ":"))
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        try:
            if os.path.exists(temporary):
                os.remove(temporary)
        except OSError:
            pass


def _cancel_requested(path):
    if not path:
        return False
    try:
        with open(path, "r", encoding="utf-8") as control:
            value = json.load(control)
        return isinstance(value, dict) and value.get("cancel") is True
    except FileNotFoundError:
        return False
    except (OSError, ValueError, TypeError):
        # A transient/invalid control snapshot must never cancel an install.
        return False



@contextmanager
def _exclusive_job_lock(path):
    """Interoperate with Android JNI flock(2) on the per-job lock inode."""
    if not path or not os.path.isabs(path):
        raise ValueError("job lock path must be an absolute path")
    with open(path, "a+b") as lock_file:
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)


def _install(core, payload):
    _require_login(core)
    app_name = str(payload.get("app_name", "")).strip()
    base_path = str(payload.get("base_path", "")).strip()
    if not app_name or not base_path:
        raise ValueError("app_name and base_path are required")
    if not os.path.isabs(base_path):
        raise ValueError("base_path must be an absolute shared-storage path")

    control_path = str(payload.get("job_control_path", "")).strip()
    status_path = str(payload.get("job_status_path", "")).strip()
    lock_path = str(payload.get("job_lock_path", "")).strip()
    if len({bool(control_path), bool(status_path), bool(lock_path)}) != 1:
        raise ValueError("job control, status, and lock paths must be supplied together")
    if control_path and any(not os.path.isabs(path) for path in (control_path, status_path, lock_path)):
        raise ValueError("job control, status, and lock paths must be absolute")

    # Avoid library metadata calls and downloader/shared-memory setup when a
    # cancellation request is already waiting.
    if _cancel_requested(control_path):
        _write_job_status(status_path, "cancelled", phase="cancelled")
        return {"cancelled": True, "app_name": app_name}

    game = core.get_game(app_name, update_meta=True, platform="Windows")
    if not game:
        raise ValueError("This title is not available in the Windows Epic library")
    if game.third_party_store:
        raise ValueError("This title requires a third-party store and cannot be installed by Legendary")
    # Metadata lookup may take time; re-check before allocating downloader state.
    if _cancel_requested(control_path):
        _write_job_status(status_path, "cancelled", phase="cancelled")
        return {"cancelled": True, "app_name": app_name}

    dlm, analysis, installed_game = core.prepare_download(
        game,
        base_path=base_path,
        platform="Windows",
        max_workers=max(1, min(int(payload.get("max_workers", 2)), 4)),
        max_shm=max(128, min(int(payload.get("max_shared_memory_mib", 256)), 512)),
        dl_timeout=30,
        # Legendary's pinned UIUpdate queue is the supported progress channel.
        # Android patching converts multiprocessing.Queue to queue.Queue.
        status_q=Queue(),
    )
    conditions = core.check_installation_conditions(analysis, installed_game, game)
    if conditions.failures:
        raise RuntimeError("; ".join(sorted(conditions.failures)))

    # Honor a request that arrived while downloader analysis ran.
    if _cancel_requested(control_path):
        _write_job_status(status_path, "cancelled", phase="cancelled")
        return {"cancelled": True, "app_name": app_name}

    _write_job_status(status_path, "running", phase="preparing", progress=0)
    dlm.start()
    cancelled = False
    last_update = None
    try:
        while dlm.is_alive():
            if not cancelled and _cancel_requested(control_path):
                cancelled = True
                _write_job_status(status_path, "cancelling", phase="stopping")
                stop = getattr(dlm, "stop", None)
                if callable(stop):
                    stop()
                else:
                    raise RuntimeError("The Android downloader does not support cooperative cancellation")

            # Legendary's UIUpdate objects are optional across pinned revisions.
            # Drain only when this manager exposes a non-blocking status queue.
            status_queue = getattr(dlm, "status_queue", None)
            if status_queue is not None:
                try:
                    while True:
                        update = status_queue.get_nowait()
                        progress = getattr(update, "progress", None)
                        values = {
                            "phase": "downloading",
                            "progress": progress,
                            "download_speed": getattr(update, "download_speed", None),
                            "write_speed": getattr(update, "write_speed", None),
                            "read_speed": getattr(update, "read_speed", None),
                        }
                        last_update = values
                except Exception:
                    pass
            if last_update:
                _write_job_status(status_path, "cancelling" if cancelled else "running", **last_update)
            elif status_path:
                _write_job_status(status_path, "cancelling" if cancelled else "running",
                                  phase="downloading")
            dlm.join(timeout=0.5)
    finally:
        if dlm.is_alive() and cancelled:
            # Do not close downloader-owned mmap state until every worker has exited.
            dlm.join()

    if dlm.is_alive():
        raise RuntimeError("Legendary download worker did not stop")
    # Close the race where cancellation arrives as the final downloader worker
    # exits: the loop condition can become false before observing the control file.
    if not cancelled and _cancel_requested(control_path):
        cancelled = True
    if cancelled:
        _write_job_status(status_path, "cancelled", phase="cancelled")
        return {"cancelled": True, "app_name": app_name}
    worker_error = getattr(dlm, "error", None)
    if worker_error:
        raise RuntimeError(f"Legendary download failed: {worker_error}")
    # Serialize the final cancellation check and phase transition against the
    # provider's cancel_job handler. Whichever side acquires flock first wins:
    # cancellation is published before installing, or installing is published
    # before the provider re-checks eligibility and rejects cancellation.
    with _exclusive_job_lock(lock_path):
        if _cancel_requested(control_path):
            cancelled = True
        else:
            _write_job_status(status_path, "running", phase="installing", progress=100)
            cancelled = False
    if cancelled:
        _write_job_status(status_path, "cancelled", phase="cancelled")
        return {"cancelled": True, "app_name": app_name}
    prerequisites = core.install_game(installed_game) or {}
    return {
        "app_name": installed_game.app_name,
        "prerequisites": prerequisites,
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
    secret_files = []
    egl_parameters = list(params.egl_parameters or [])
    for index, argument in enumerate(egl_parameters):
        if argument.startswith("-epicovt="):
            source_path = argument.split("=", 1)[1]
            try:
                with open(source_path, "rb") as source:
                    token_data = source.read(256 * 1024 + 1)
            finally:
                try:
                    os.remove(source_path)
                except OSError:
                    pass
            if len(token_data) > 256 * 1024:
                raise ValueError("Epic ownership token file exceeded the safe transfer limit")
            secret_files.append({
                "name": f"epic-{uuid.uuid4().hex}.ovt",
                "data_base64": base64.b64encode(token_data).decode("ascii"),
            })
            egl_parameters[index] = "-epicovt=__EPIC_OVT_PLACEHOLDER__"

    return {
        "app_name": app_name,
        "game_executable": params.game_executable,
        "game_directory": params.game_directory,
        "working_directory": params.working_directory or params.game_directory,
        "game_parameters": list(params.game_parameters or []),
        "egl_parameters": egl_parameters,
        "user_parameters": list(params.user_parameters or []),
        "environment": dict(params.environment or {}),
        "secret_files": secret_files,
    }


def _runtime_self_test():
    import importlib.metadata
    import importlib.resources
    import importlib.util
    import sys
    import zipfile
    import zipfile._path
    if not hasattr(zipfile, "__path__") or importlib.util.find_spec("zipfile._path") is None:
        raise RuntimeError("CPython zipfile package is not available on sys.path")
    if importlib.resources.files("legendary") is None:
        raise RuntimeError("importlib.resources could not resolve Legendary package resources")
    import ssl
    import tempfile
    from queue import Queue
    from threading import Condition, Thread
    from types import SimpleNamespace

    import certifi
    import filelock
    import requests
    from legendary.android_crypto import AES
    from legendary.core import LegendaryCore
    from legendary.downloader.mp.android_shared_buffer import AndroidSharedBuffer
    from legendary.downloader.mp.manager import DLManager
    from legendary.downloader.mp.workers import DLWorker, FileWorker
    from legendary.models.downloading import AnalysisResult, FileTask, TaskFlags, TerminateWorkerTask, WriterTaskResult

    # Cancellation before or during metadata lookup must not allocate a
    # downloader or its shared-memory mapping. These fake cores never contact Epic.
    class CancelledInstallCore:
        def __init__(self, control_path=None, cancel_on_lookup=False):
            self.control_path = control_path
            self.cancel_on_lookup = cancel_on_lookup
            self.lookup_called = False
        def login(self):
            return True
        def get_game(self, *args, **kwargs):
            self.lookup_called = True
            if self.cancel_on_lookup:
                with open(self.control_path, "w", encoding="utf-8") as control:
                    json.dump({"cancel": True}, control)
            return SimpleNamespace(third_party_store="")
        def prepare_download(self, *args, **kwargs):
            raise AssertionError("cancelled install unexpectedly prepared downloader")

    with tempfile.TemporaryDirectory(prefix="epic-cancel-before-prepare-") as cancel_dir:
        control_path = os.path.join(cancel_dir, "control.json")
        status_path = os.path.join(cancel_dir, "status.json")
        payload = {
            "app_name": "cancel-smoke",
            "base_path": cancel_dir,
            "job_control_path": control_path,
            "job_status_path": status_path,
            "job_lock_path": os.path.join(cancel_dir, "job.lock"),
        }
        # Already cancelled: metadata lookup itself must be skipped.
        with open(control_path, "w", encoding="utf-8") as control:
            json.dump({"cancel": True}, control)
        pre_cancel_core = CancelledInstallCore()
        cancelled_result = _install(pre_cancel_core, payload)
        if not cancelled_result.get("cancelled") or pre_cancel_core.lookup_called:
            raise RuntimeError("pre-metadata cancellation was not honored")

        # Cancellation arriving during metadata lookup must be observed before
        # prepare_download() allocates downloader/shared-memory state.
        with open(control_path, "w", encoding="utf-8") as control:
            json.dump({"cancel": False}, control)
        metadata_cancel_core = CancelledInstallCore(control_path, cancel_on_lookup=True)
        cancelled_result = _install(metadata_cancel_core, payload)
        if not cancelled_result.get("cancelled") or not metadata_cancel_core.lookup_called:
            raise RuntimeError("metadata-stage cancellation was not honored")
        with open(status_path, "r", encoding="utf-8") as status:
            if json.load(status).get("state") != "cancelled":
                raise RuntimeError("metadata-stage cancellation status was not published")

    # Catalog artwork selection must prefer a useful portrait and reject
    # non-HTTPS or malformed metadata without requiring an Epic network session.
    artwork_game = SimpleNamespace(metadata={
        "keyImages": [
            {"type": "OfferImageWide", "url": "https://cdn1.epicgames.com/wide.jpg"},
            {"type": "DieselGameBoxTall", "url": "https://cdn1.epicgames.com/box.jpg"},
            {"type": "Thumbnail", "url": "http://insecure.example/thumb.jpg"},
        ]
    })
    if _cover_image(artwork_game) != "https://cdn1.epicgames.com/box.jpg":
        raise RuntimeError("Epic catalog portrait artwork selection failed")
    if _cover_image(SimpleNamespace(metadata={"keyImages": [{"url": "file:///private/path"}]})):
        raise RuntimeError("Epic catalog artwork accepted a non-HTTPS URL")

    core = _core()
    if not isinstance(core, LegendaryCore):
        raise RuntimeError("Legendary core did not initialize in the Android runtime")
    core.get_installed_list()

    ciphertext = bytes.fromhex("0388dace60b6a392f328c2b971b2fe78")
    tag = bytes.fromhex("ab6e47d42cec13bdf53a67b21257bddf")
    plaintext = AES.new(bytes(16), AES.MODE_GCM, nonce=bytes(12)).decrypt_and_verify(ciphertext, tag)
    if plaintext != bytes(16):
        raise RuntimeError("Android JCA AES-GCM NIST vector failed")

    worker_results = []
    def decrypt_on_worker():
        try:
            worker_results.append(
                AES.new(bytes(16), AES.MODE_GCM, nonce=bytes(12))
                .decrypt_and_verify(ciphertext, tag)
            )
        except BaseException as error:
            worker_results.append(error)

    crypto_worker = Thread(target=decrypt_on_worker, name="epic-jca-worker-smoke")
    crypto_worker.start()
    crypto_worker.join(timeout=10)
    if crypto_worker.is_alive():
        raise RuntimeError("Android JCA AES-GCM worker-thread call timed out")
    if worker_results != [bytes(16)]:
        raise RuntimeError(f"Android JCA AES-GCM worker-thread call failed: {worker_results!r}")

    bad_tag = bytes([tag[0] ^ 1]) + tag[1:]
    try:
        AES.new(bytes(16), AES.MODE_GCM, nonce=bytes(12)).decrypt_and_verify(ciphertext, bad_tag)
    except ValueError:
        # The native shim deliberately maps JCA authentication failures to
        # ValueError, matching the decrypt-and-verify contract. The valid
        # NIST vector above proves this is not a blanket crypto failure.
        bad_tag_rejected = True
    else:
        raise RuntimeError("Android JCA AES-GCM accepted a corrupted authentication tag")

    ssl.create_default_context(cafile=certifi.where())
    if importlib.metadata.version("legendary-gl") != "0.21.1":
        raise RuntimeError("Unexpected Legendary distribution version")

    manager = DLManager("/tmp/epic-runtime-smoke", "https://cdn.invalid", {})
    if not isinstance(manager, Thread) or manager.error is not None:
        raise RuntimeError("Legendary download manager thread backend is invalid")
    manager.stop()

    # A no-op update must not start workers or fail on an empty task deque.
    empty_manager = DLManager("/tmp/epic-runtime-empty-smoke", "https://cdn.invalid", {})
    empty_manager.analysis = AnalysisResult()
    empty_manager.start()
    empty_manager.join(timeout=5)
    if empty_manager.is_alive() or empty_manager.error is not None:
        raise RuntimeError("Legendary empty update did not exit cleanly")

    # File-only installs (empty files/deletions) have no chunk denominator.
    with tempfile.TemporaryDirectory(prefix="epic-empty-install-") as empty_dir:
        file_only_manager = DLManager(
            empty_dir, "https://cdn.invalid", {}, max_workers=1, max_shared_memory=4096
        )
        file_only_manager.analysis = AnalysisResult(biggest_chunk=1024)
        file_only_manager.tasks.append(
            FileTask(filename="empty.bin", flags=TaskFlags.CREATE_EMPTY_FILE)
        )
        file_only_manager.start()
        file_only_manager.join(timeout=10)
        if file_only_manager.is_alive() or file_only_manager.error is not None:
            raise RuntimeError("Legendary file-only install failed")
        if not os.path.isfile(os.path.join(empty_dir, "empty.bin")):
            raise RuntimeError("Legendary file-only install did not create its output")

    failed_manager = DLManager("/tmp/epic-runtime-failure-smoke", "https://cdn.invalid", {})
    failed_manager.analysis = AnalysisResult()
    def fail_download_manager():
        raise RuntimeError("intentional download-manager cleanup probe")
    failed_manager.run_real = fail_download_manager
    failed_manager.start()
    failed_manager.join(timeout=5)
    if failed_manager.is_alive() or not isinstance(failed_manager.error, RuntimeError):
        raise RuntimeError("Legendary download-manager exception cleanup failed")

    # A failed writer result must stop the install and reach the bridge caller.
    failure_manager = DLManager("/tmp/epic-runtime-smoke", "https://cdn.invalid", {})
    failure_manager.conditions = [Condition()]
    failure_manager.writer_result_q = Queue()
    failure_manager.writer_result_q.put(WriterTaskResult(
        filename="failed.bin", flags=TaskFlags.NONE, success=False
    ))
    failure_manager.writer_result_q.put(TerminateWorkerTask())
    failure_manager.fw_results_handler(Condition())
    if failure_manager.running or not isinstance(failure_manager.error, RuntimeError):
        raise RuntimeError("Legendary writer failures are not propagated")

    buffer = AndroidSharedBuffer(4096)
    root_logger = logging.getLogger()
    marker_handler = logging.NullHandler()
    root_logger.addHandler(marker_handler)
    root_handlers_before = tuple(root_logger.handlers)
    try:
        buffer.buf[:8] = b"MALIWINL"
        if buffer.buf[:8] != b"MALIWINL":
            raise RuntimeError("Anonymous mmap read/write test failed")
        logs, dl_q, dl_results, writer_q, writer_results = Queue(), Queue(), Queue(), Queue(), Queue()
        dl_worker = DLWorker("epic-runtime-dl-smoke", dl_q, dl_results, buffer, logging_queue=logs)
        writer_worker = FileWorker(writer_q, writer_results, "/tmp/epic-runtime-smoke",
                                   buffer, logging_queue=logs)
        if not isinstance(dl_worker, Thread) or not isinstance(writer_worker, Thread):
            raise RuntimeError("Legendary workers are not threads")
        dl_worker.start()
        writer_worker.start()
        dl_q.put(TerminateWorkerTask())
        writer_q.put(TerminateWorkerTask())
        dl_worker.join(timeout=5)
        writer_worker.join(timeout=5)
        if dl_worker.is_alive() or writer_worker.is_alive():
            raise RuntimeError("Legendary worker thread shutdown timed out")
        if not isinstance(writer_results.get(timeout=1), TerminateWorkerTask):
            raise RuntimeError("Legendary writer did not report clean shutdown")
        if tuple(root_logger.handlers) != root_handlers_before:
            raise RuntimeError("Legendary worker threads mutated the process-wide root logger")
    finally:
        root_logger.removeHandler(marker_handler)
        buffer.close()
        buffer.unlink()

    return {
        "python": sys.version.split()[0],
        "zipfile_module": zipfile.__file__,
        "zipfile_path_module": zipfile._path.__file__,
        "python_search_paths": sys.path,
        "legendary": importlib.metadata.version("legendary-gl"),
        "requests": requests.__version__,
        "certifi": certifi.where(),
        "android_jca_aes_gcm": True,
        "android_jca_aes_gcm_worker_thread": True,
        "android_jca_aes_gcm_bad_tag_rejected": bad_tag_rejected,
        "thread_workers": True,
        "download_failure_cleanup": True,
        "anonymous_mmap": True,
    }


def handle(command: str, payload_json: str = "{}") -> str:
    try:
        payload = json.loads(payload_json or "{}")
        if not isinstance(payload, dict):
            raise ValueError("payload must be a JSON object")

        if command == "begin_login":
            from legendary.api.egs import EPCAPI
            return _dump({"ok": True, "authorization_url": EPCAPI().get_auth_url()})

        if command == "runtime_self_test":
            return _dump({"ok": True, "self_test": _runtime_self_test()})

        if command == "job_lock_probe":
            lock_path = str(payload.get("job_lock_path", "")).strip()
            attempt_path = str(payload.get("attempt_path", "")).strip()
            if attempt_path:
                with open(attempt_path, "w", encoding="utf-8") as marker:
                    marker.write("attempting")
            with _exclusive_job_lock(lock_path):
                return _dump({"ok": True, "lock_acquired": True})

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

        if command == "game_details":
            return _dump({"ok": True, "game": _game_details(core, payload)})

        if command == "installed":
            return _dump({"ok": True, "games": _installed(core)})

        if command == "install":
            result = _install(core, payload)
            if result.get("cancelled"):
                return _dump({"ok": False, "cancelled": True,
                              "error": "OPERATION_CANCELLED",
                              "message": "Epic installation was cancelled"})
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
        # Exception text and tracebacks can contain private install paths,
        # account metadata, or request details. Never serialize exception contents.
        log.error("Epic Bridge command failed (command=%s, exception=%s)",
                  command, type(exc).__name__)
        return _dump({
            "ok": False,
            "error": "EPIC_OPERATION_FAILED",
            "message": "Epic operation failed. Review the app diagnostic log.",
        })
