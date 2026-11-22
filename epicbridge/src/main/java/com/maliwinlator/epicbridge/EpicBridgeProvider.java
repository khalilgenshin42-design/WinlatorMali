package com.maliwinlator.epicbridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class EpicBridgeProvider extends ContentProvider {
    public static final String AUTHORITY = "com.maliwinlator.epicbridge.provider";
    public static final int PROTOCOL_VERSION = 2;
    static final String HOST_PACKAGE = "com.maliwinlator.app";
    private static volatile Boolean cryptoSelfTest;

    @Override
    public boolean onCreate() {
        return true;
    }

    static boolean isAllowedHostPackage(String packageName) {
        return HOST_PACKAGE.equals(packageName);
    }

    static boolean isAllowedHostUidPackages(String[] packageNames) {
        if (packageNames == null) return false;
        for (String packageName : packageNames) {
            if (isAllowedHostPackage(packageName)) return true;
        }
        return false;
    }

    static boolean shouldMarkJobInterrupted(String state, long startedAt,
            long now, boolean active) {
        return "running".equals(state) && startedAt > 0
                && now - startedAt > 30_000L && !active;
    }

    private boolean isAuthorizedHost() {
        if (getContext() == null) return false;
        int callingUid = Binder.getCallingUid();
        // The future in-package runtime provider is private (exported=false)
        // and executes under the host application's UID. Permit that same-UID
        // call for every product flavor; retain the explicit host-package gate
        // for the separately installed companion provider.
        if (callingUid == android.os.Process.myUid()) return true;
        String[] packages = getContext().getPackageManager().getPackagesForUid(callingUid);
        return isAllowedHostUidPackages(packages);
    }

    /**
     * Publish cancellation by atomically replacing the control snapshot. A
     * reader must see either the previous complete JSON object or the new one,
     * never a truncated file that could lose a cancellation request.
     */
    static void writeCancelRequest(File controlFile) throws Exception {
        File parent = controlFile.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            throw new IllegalStateException("Job control directory is unavailable");
        }
        File temporary = new File(parent, controlFile.getName() + ".tmp-" + java.util.UUID.randomUUID());
        try {
            try (FileOutputStream output = new FileOutputStream(temporary, false)) {
                output.write("{\"cancel\":true}".getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            // Both paths are in the same private directory, so Android/Linux
            // rename publishes the fully-synced snapshot atomically.
            if (!temporary.renameTo(controlFile)) {
                throw new java.io.IOException("Unable to publish cancellation request");
            }
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    static String readProgressPhase(String snapshot) {
        if (snapshot == null) return null;
        try {
            String phase = new org.json.JSONObject(snapshot).optString("phase", "");
            return phase.isEmpty() ? null : phase;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Read a bounded progress snapshot without trusting the file's initial length. */
    static String readProgressSnapshot(File statusFile) {
        if (statusFile == null || !statusFile.isFile()) return null;
        try (FileInputStream input = new FileInputStream(statusFile);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[1024];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (buffer.size() + count > 8192) return null;
                buffer.write(chunk, 0, count);
            }
            return buffer.toString("UTF-8");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean cryptoSelfTest() {
        Boolean cached = cryptoSelfTest;
        if (cached != null) return cached;
        synchronized (EpicBridgeProvider.class) {
            if (cryptoSelfTest == null) {
                cryptoSelfTest = EpicCryptoBridge.verifyKnownAnswerTest();
            }
            return cryptoSelfTest;
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        result.putInt("protocol_version", PROTOCOL_VERSION);
        if (!isAuthorizedHost()) {
            result.putBoolean("ok", false);
            result.putString("error", "CALLER_NOT_ALLOWED");
            result.putString("message", "Only the MaliWinlator host application may access this bridge");
            return result;
        }

        if ("status".equals(method) || "ping".equals(method)) {
            boolean cryptoReady = cryptoSelfTest();
            boolean runtimePackaged = EpicPythonRuntime.isPackaged(getContext());
            result.putBoolean("installed", true);
            // The embedded provider shares Android's all-files permission with
            // Winlator; the standalone companion has its own package grant.
            result.putBoolean("host_embedded",
                    !"com.maliwinlator.epicbridge".equals(getContext().getPackageName()));
            result.putBoolean("crypto_supported", cryptoReady);
            result.putBoolean("runtime_ready", runtimePackaged && cryptoReady);
            result.putBoolean("storage_access",
                    Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager());
            result.putString("state", runtimePackaged
                    ? (cryptoReady ? "runtime_ready" : "crypto_unavailable")
                    : "runtime_not_installed");
            result.putString("crypto_status", cryptoReady
                    ? "Android AES-GCM self-test passed" : "Android AES-GCM unavailable");
            result.putString("message", runtimePackaged
                    ? (cryptoReady ? "Legendary Android runtime is packaged" : "Android AES-GCM self-test failed")
                    : "Epic runtime is not bundled in this companion build");
            return result;
        }

        if (!EpicPythonRuntime.isPackaged(getContext())) {
            result.putBoolean("ok", false);
            result.putString("error", "RUNTIME_NOT_READY");
            result.putString("message", "This Epic Bridge package does not contain the Android CPython runtime");
            return result;
        }

        if ("start_job".equals(method)) {
            String command = arg == null ? "" : arg;
            if (!"install".equals(command) && !"uninstall".equals(command)) {
                result.putBoolean("ok", false);
                result.putString("error", "UNSUPPORTED_JOB");
                return result;
            }
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                result.putBoolean("ok", false);
                result.putString("error", "STORAGE_PERMISSION_REQUIRED");
                result.putString("message", "Grant all-files access to Epic Bridge before installing or uninstalling games");
                return result;
            }
            String jobId = java.util.UUID.randomUUID().toString();
            if (!EpicRuntimeService.reserveJob(jobId)) {
                result.putBoolean("ok", false);
                result.putString("error", "JOB_ALREADY_RUNNING");
                result.putString("message", "Another Epic install or uninstall operation is already running");
                return result;
            }
            String payload = extras == null ? "{}" : extras.getString("payload_json", "{}");
            android.content.Intent intent = new android.content.Intent(getContext(), EpicRuntimeService.class);
            intent.setAction(EpicRuntimeService.ACTION_RUN_JOB);
            intent.putExtra(EpicRuntimeService.EXTRA_JOB_ID, jobId);
            intent.putExtra(EpicRuntimeService.EXTRA_COMMAND, command);
            intent.putExtra(EpicRuntimeService.EXTRA_PAYLOAD, payload);
            try {
                getContext().startForegroundService(intent);
                result.putBoolean("ok", true);
                result.putString("job_id", jobId);
                result.putString("state", "queued");
            } catch (Exception e) {
                EpicRuntimeService.releaseJobReservation(jobId);
                result.putBoolean("ok", false);
                // Android exception messages can include private filesystem paths.
                result.putString("error", "JOB_START_FAILED");
                result.putString("message", "Unable to start the Epic background job");
            }
            return result;
        }

        if ("cancel_job".equals(method)) {
            String jobId = arg == null ? "" : arg;
            android.content.SharedPreferences prefs = getContext().getSharedPreferences(
                    EpicRuntimeService.PREFS, android.content.Context.MODE_PRIVATE);
            String lockPath = prefs.getString(jobId + ".lock_path", "");
            if (lockPath.isEmpty()) {
                result.putBoolean("ok", false);
                result.putString("error", "JOB_LOCK_UNAVAILABLE");
                return result;
            }
            try (EpicJobLock ignored = EpicJobLock.acquire(new File(lockPath))) {
                // Re-read every eligibility input while holding the same lock
                // Python takes before its final cancel check and installing transition.
                String statusPath = prefs.getString(jobId + ".status_path", "");
                String progressSnapshot = statusPath.isEmpty()
                        ? null : readProgressSnapshot(new File(statusPath));
                String phase = readProgressPhase(progressSnapshot);
                if (!EpicRuntimeService.isJobCancellable(jobId,
                        prefs.getString(jobId + ".state", "unknown"),
                        prefs.getString(jobId + ".command", ""), phase)) {
                    result.putBoolean("ok", false);
                    result.putString("error", "JOB_NOT_CANCELLABLE");
                    result.putString("message", "The Epic job is no longer in a cancellable download phase");
                    return result;
                }
                String controlPath = prefs.getString(jobId + ".control_path", "");
                if (controlPath.isEmpty()) {
                    result.putBoolean("ok", false);
                    result.putString("error", "JOB_CONTROL_UNAVAILABLE");
                    return result;
                }
                writeCancelRequest(new File(controlPath));
                result.putBoolean("ok", true);
                result.putString("state", "cancelling");
            } catch (Exception failure) {
                result.putBoolean("ok", false);
                result.putString("error", "JOB_CANCEL_FAILED");
                result.putString("message", "Unable to request Epic job cancellation");
            }
            return result;
        }

        if ("job_status".equals(method)) {
            String jobId = arg == null ? "" : arg;
            android.content.SharedPreferences prefs = getContext().getSharedPreferences(
                    EpicRuntimeService.PREFS, android.content.Context.MODE_PRIVATE);
            String jobState = prefs.getString(jobId + ".state", "unknown");
            long jobStartedAt = prefs.getLong(jobId + ".started_at", 0L);
            if (shouldMarkJobInterrupted(jobState, jobStartedAt,
                    System.currentTimeMillis(), EpicRuntimeService.isJobActive(jobId))) {
                jobState = "interrupted";
                prefs.edit().putString(jobId + ".state", jobState)
                        .putLong(jobId + ".finished_at", System.currentTimeMillis()).apply();
            }
            result.putBoolean("ok", true);
            result.putString("job_id", jobId);
            result.putString("state", jobState);
            result.putString("result_json", prefs.getString(jobId + ".result", ""));
            String statusPath = prefs.getString(jobId + ".status_path", "");
            String progress = statusPath.isEmpty()
                    ? null : readProgressSnapshot(new File(statusPath));
            if (progress != null) result.putString("progress_json", progress);
            result.putBoolean("can_cancel", EpicRuntimeService.isJobCancellable(jobId,
                    jobState, prefs.getString(jobId + ".command", ""),
                    readProgressPhase(progress)));
            return result;
        }

        switch (method) {
            case "begin_login":
            case "finish_login":
            case "account_status":
            case "logout":
            case "library":
            case "installed":
            case "install":
            case "uninstall":
            case "launch_info":
            case "runtime_self_test": {
                if (("install".equals(method) || "uninstall".equals(method))
                        && Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                    result.putBoolean("ok", false);
                    result.putString("error", "STORAGE_PERMISSION_REQUIRED");
                    result.putString("message", "Grant all-files access to Epic Bridge before installing or uninstalling games");
                    return result;
                }
                String payload = extras == null ? "{}" : extras.getString("payload_json", "{}");
                String runtimeCommand = "account_status".equals(method) ? "status" : method;
                String response = EpicPythonRuntime.call(getContext(), runtimeCommand, payload);
                result.putString("result_json", response);
                result.putBoolean("ok", response != null);
                return result;
            }
            default:
                result.putBoolean("ok", false);
                result.putString("error", "UNKNOWN_METHOD");
                result.putString("message", "Unsupported Epic Bridge operation");
                return result;
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
