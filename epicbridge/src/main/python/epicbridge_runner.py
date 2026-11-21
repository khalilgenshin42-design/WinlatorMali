"""Structured Android service API over pinned Legendary 0.21.1."""
from __future__ import annotations

import base64
import uuid
import json
import logging
import os
from queue import Queue

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
            "dlcs": dlcs,
        })
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
        # Legendary's pinned UIUpdate queue is the supported progress channel.
        # Android patching converts multiprocessing.Queue to queue.Queue.
        status_q=Queue(),
    )
    conditions = core.check_installation_conditions(analysis, installed_game, game)
    if conditions.failures:
        raise RuntimeError("; ".join(sorted(conditions.failures)))

    control_path = str(payload.get("job_control_path", "")).strip()
    status_path = str(payload.get("job_status_path", "")).strip()
    if bool(control_path) != bool(status_path):
        raise ValueError("job control and status paths must be supplied together")
    if control_path and (not os.path.isabs(control_path) or not os.path.isabs(status_path)):
        raise ValueError("job control and status paths must be absolute")

    # Honor a request that arrived while metadata/installation analysis ran.
    # Do not create downloader threads or mmap state when cancellation is already set.
    if _cancel_requested(control_path):
        _write_job_status(status_path, "cancelled", phase="cancelled")
        return {"cancelled": True, "app_name": app_name}

    _write_job_status(status_path, "running", phase="preparing", progress=0)
    dlm.start()
    cancelled = False
    last_update = None
    try:
        while dlm.is_alive():
            if _cancel_requested(control_path):
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
    # Publish the non-cancellable phase before the final control-file check.
    # The provider uses this phase to reject late cancellation requests.
    _write_job_status(status_path, "running", phase="installing", progress=100)
    if _cancel_requested(control_path):
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

    import certifi
    import filelock
    import requests
    from legendary.android_crypto import AES
    from legendary.core import LegendaryCore
    from legendary.downloader.mp.android_shared_buffer import AndroidSharedBuffer
    from legendary.downloader.mp.manager import DLManager
    from legendary.downloader.mp.workers import DLWorker, FileWorker
    from legendary.models.downloading import AnalysisResult, FileTask, TaskFlags, TerminateWorkerTask, WriterTaskResult

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
