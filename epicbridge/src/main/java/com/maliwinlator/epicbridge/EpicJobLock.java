package com.maliwinlator.epicbridge;

import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileDescriptor;

/** Cross-runtime advisory lock for one Epic job's cancel/install phase boundary. */
final class EpicJobLock implements AutoCloseable {
    private final FileDescriptor descriptor;

    private EpicJobLock(FileDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    static EpicJobLock acquire(File file) throws Exception {
        File parent = file.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            throw new IllegalStateException("Epic job lock directory is unavailable");
        }
        FileDescriptor descriptor = Os.open(file.getAbsolutePath(),
                OsConstants.O_CREAT | OsConstants.O_RDWR, 0600);
        try {
            Os.flock(descriptor, OsConstants.LOCK_EX);
            return new EpicJobLock(descriptor);
        } catch (Exception failure) {
            try { Os.close(descriptor); } catch (Exception ignored) {}
            throw failure;
        }
    }

    @Override
    public void close() throws Exception {
        try {
            Os.flock(descriptor, OsConstants.LOCK_UN);
        } finally {
            Os.close(descriptor);
        }
    }
}
