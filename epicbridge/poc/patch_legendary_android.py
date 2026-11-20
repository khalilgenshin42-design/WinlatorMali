#!/usr/bin/env python3
"""Apply the Android/Bionic thread-backend patch to pinned Legendary 0.21.1.

This patch is intentionally limited to the separately licensed Android
companion source tree. Desktop Legendary builds remain unchanged.
"""
from __future__ import annotations

import sys
from pathlib import Path


def replace_once(path: Path, old: str, new: str, expected: int = 1) -> None:
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != expected:
        raise SystemExit(
            f"{path}: expected {expected} occurrence(s) for patch anchor, found {count}: {old!r}"
        )
    path.write_text(source.replace(old, new), encoding="utf-8")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_legendary_android.py LEGENDARY_SOURCE_DIR")
    root = Path(sys.argv[1]).resolve()
    manager = root / "legendary/downloader/mp/manager.py"
    workers = root / "legendary/downloader/mp/workers.py"
    core = root / "legendary/core.py"
    cli = root / "legendary/cli.py"

    if not all(p.is_file() for p in (manager, workers, core, cli)):
        raise SystemExit(f"not a Legendary source tree: {root}")

    replace_once(
        manager,
        "from multiprocessing import Process, cpu_count\n"
        "from multiprocessing import Queue as MPQueue\n"
        "from multiprocessing.shared_memory import SharedMemory\n",
        "from queue import Queue as MPQueue\n",
        expected=1,
    )
    replace_once(
        manager,
        "class DLManager(Process):",
        "class DLManager(Thread):",
    )
    replace_once(
        manager,
        "self.max_workers = max_workers or min(cpu_count() * 2, 16)",
        "self.max_workers = max(1, min(max_workers or (os.cpu_count() or 1), 4))",
    )
    replace_once(
        manager,
        "self.max_shared_memory = max_shared_memory  # 1 GiB by default",
        "self.max_shared_memory = min(max_shared_memory, 512 * 1024 * 1024)  # Android 512 MiB cap",
    )
    replace_once(
        manager,
        "self.shared_memory = SharedMemory(create=True, size=self.max_shared_memory)",
        "self.shared_memory = AndroidSharedBuffer(self.max_shared_memory)",
    )
    replace_once(
        manager,
        "self.shared_memory.name, logging_queue=self.logging_queue,",
        "self.shared_memory, logging_queue=self.logging_queue,",
    )
    replace_once(
        manager,
        "self.shared_memory.name, self.cache_dir, self.logging_queue,",
        "self.shared_memory, self.cache_dir, self.logging_queue,",
    )
    replace_once(
        manager,
        "        writer_p.join(timeout=10.0)\n"
        "        if writer_p.exitcode is None:\n"
        "            self.log.warning('Terminating writer process, no exit code!')\n"
        "            writer_p.terminate()\n\n"
        "        # forcibly kill DL workers that are not actually dead yet\n"
        "        for child in self.children:\n"
        "            if child.exitcode is None:\n"
        "                child.terminate()",
        "        writer_p.join(timeout=30.0)\n"
        "        if writer_p.is_alive():\n"
        "            self.log.warning('Writer thread did not terminate after its sentinel.')\n\n"
        "        for child in self.children:\n"
        "            child.join(timeout=30.0)\n"
        "            if child.is_alive():\n"
        "                self.log.warning(f'Worker thread did not terminate: {child!r}')",
    )
    replace_once(
        manager,
        "            for child in self.children:\n"
        "                child.join(timeout=5.0)\n"
        "                if child.exitcode is None:\n"
        "                    child.terminate()",
        "            for child in self.children:\n"
        "                child.join(timeout=10.0)\n"
        "                if child.is_alive():\n"
        "                    self.log.warning(f'Worker thread did not terminate: {child!r}')",
    )
    replace_once(
        manager,
        "                except Empty:\n"
        "                    q.close()\n"
        "                    q.join_thread()",
        "                except Empty:\n"
        "                    break",
    )
    replace_once(
        manager,
        "        while processed_tasks < num_tasks:",
        "        while processed_tasks < num_tasks and self.running:",
    )
    replace_once(
        manager,
        "        self.log.info(f'Download Manager running with process-id: {os.getpid()}')",
        "        self.log.info('Download Manager running with Android thread workers')",
    )

    # The Android manager owns the same bounded mmap buffer for all worker
    # threads. mmap allocates virtual address space and commits pages as used.
    shared_buffer = root / "legendary/downloader/mp/android_shared_buffer.py"
    shared_buffer.write_text(
        '"""In-process bounded buffer for Android thread-based downloads."""\n'
        "import mmap\n\n\n"
        "class AndroidSharedBuffer:\n"
        "    def __init__(self, size):\n"
        "        if size <= 0:\n"
        "            raise ValueError('shared buffer size must be positive')\n"
        "        self.size = int(size)\n"
        "        self.name = 'android-thread-buffer'\n"
        "        self.buf = mmap.mmap(-1, self.size, access=mmap.ACCESS_WRITE)\n\n"
        "    def close(self):\n"
        "        self.buf.close()\n\n"
        "    def unlink(self):\n"
        "        # Anonymous mmap has no filesystem name to unlink.\n"
        "        return None\n",
        encoding="utf-8",
    )
    replace_once(
        manager,
        "from legendary.downloader.mp.workers import DLWorker, FileWorker",
        "from legendary.downloader.mp.android_shared_buffer import AndroidSharedBuffer\n"
        "from legendary.downloader.mp.workers import DLWorker, FileWorker",
    )

    replace_once(
        workers,
        "from multiprocessing import Process\n"
        "from multiprocessing.shared_memory import SharedMemory\n",
        "",
    )
    replace_once(
        workers,
        "from queue import Empty\n",
        "from queue import Empty\n"
        "from threading import Thread as Process\n",
    )
    replace_once(
        workers,
        "self.shm = SharedMemory(name=shm)",
        "self.shm = shm",
        expected=2,
    )

    replace_once(
        core,
        "from multiprocessing import Queue",
        "from queue import Queue",
    )

    replace_once(
        cli,
        "from multiprocessing import Queue as MPQueue\n"
        "from multiprocessing import freeze_support\n",
        "from queue import Queue as MPQueue\n",
    )
    replace_once(cli, "    freeze_support()\n", "")

    # Thread workers cannot be forcibly terminated. The stop method wakes
    # queue/condition waiters and asks every worker to exit cooperatively.
    anchor = "        self.num_tasks_processed_since_last = 0\n\n"
    stop_method = (
        "        self.num_tasks_processed_since_last = 0\n\n"
        "    def stop(self):\n"
        "        self.running = False\n"
        "        for cond in self.conditions:\n"
        "            with cond:\n"
        "                cond.notify_all()\n"
        "        # run_real owns ordered queue shutdown after helper threads exit.\n\n"
    )
    replace_once(manager, anchor, stop_method)

    # Use the stop path on exceptions; this is idempotent and thread-safe.
    replace_once(
        manager,
        "            self.running = False\n\n"
        "            # send conditions to unlock threads if they aren't already\n"
        "            for cond in self.conditions:\n"
        "                with cond:\n"
        "                    cond.notify()",
        "            self.stop()",
    )

    # Process-only cleanup is invalid for threads. Join cooperatively and
    # do not call multiprocessing queue lifecycle methods on queue.Queue.
    replace_once(
        manager,
        "                if child.exitcode is None:\\n"
        "                    child.terminate()",
        "                if child.is_alive():\\n"
        "                    self.log.warning(f'Worker thread did not terminate: {child!r}')",
    )
    replace_once(manager, "                    q.close()", "                    pass")
    replace_once(manager, "                    q.join_thread()", "                    pass")
    replace_once(manager, "        exit(0)", "        return")

    # Every worker receives the same mmap object. Only the manager may close it.
    replace_once(
        workers,
        "        self.shm.close()",
        "        # Shared mmap lifetime is owned by DLManager.",
        expected=1,
    )

    # CTRL-C is delivered to the CLI's main thread, not the downloader thread.
    # Convert it into an explicit cooperative stop before allowing process exit.
    replace_once(
        cli,
        "            dlm.start()\\n"
        "            dlm.join()",
        "            dlm.start()\\n"
        "            try:\\n"
        "                dlm.join()\\n"
        "            except KeyboardInterrupt:\\n"
        "                logger.warning('Interrupt requested; stopping Android download threads...')\\n"
        "                dlm.stop()\\n"
        "                dlm.join(timeout=60.0)\\n"
        "                raise",
    )

    replace_once(
        manager,
        "            while self.active_tasks < self.max_workers * 2 and self.chunks_to_dl:",
        "            while self.active_tasks < self.max_workers * 2 and self.chunks_to_dl and self.running:",
    )

    # Keep the file-writer result handler alive through cancellation so it can
    # record completed files in the resume file and release mmap segments.
    replace_once(
        manager,
        "    def fw_results_handler(self, shm_cond: Condition):\\n"
        "        while self.running:",
        "    def fw_results_handler(self, shm_cond: Condition):\\n"
        "        while True:",
    )

    # The manager's result-handler threads must stop producing writer tasks
    # before the writer termination marker is queued. On cancellation, discard
    # only not-yet-started download jobs; active workers finish their current I/O.
    replace_once(
        manager,
        "        for _ in range(self.max_workers):\\n"
        "            self.dl_worker_queue.put_nowait(TerminateWorkerTask())",
        "        for t in self.threads[:2]:\\n"
        "            t.join(timeout=30.0)\\n"
        "            if t.is_alive():\\n"
        "                self.log.warning(f'Helper thread did not terminate: {t!r}')\\n"
        "        if not self.running:\\n"
        "            try:\\n"
        "                while True:\\n"
        "                    self.dl_worker_queue.get_nowait()\\n"
        "            except Empty:\\n"
        "                pass\\n"
        "        for _ in range(self.max_workers):\\n"
        "            self.dl_worker_queue.put_nowait(TerminateWorkerTask())",
    )

    # Ensure every patched Python file parses before reporting success.
    import ast

    for path in (manager, workers, core, cli, shared_buffer):
        ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    print("LEGENDARY_ANDROID_THREAD_BACKEND_PATCHED")


if __name__ == "__main__":
    main()
