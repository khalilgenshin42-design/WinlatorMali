package com.maliwinlator.epicbridge;

import java.io.File;

/** Cross-runtime advisory lock for one Epic job's cancel/install phase boundary. */
final class EpicJobLock implements AutoCloseable {
    private final long descriptor;

    private EpicJobLock(long descriptor) {
        this.descriptor = descriptor;
    }

    static EpicJobLock acquire(File file) {
        File parent = file.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            throw new IllegalStateException("Epic job lock directory is unavailable");
        }
        return new EpicJobLock(EpicPythonRuntime.acquireJobLock(file.getAbsolutePath()));
    }

    @Override
    public void close() {
        EpicPythonRuntime.releaseJobLock(descriptor);
    }
}
