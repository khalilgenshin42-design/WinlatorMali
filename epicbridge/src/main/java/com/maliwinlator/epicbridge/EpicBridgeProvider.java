package com.maliwinlator.epicbridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;

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

    private boolean isAuthorizedHost() {
        if (getContext() == null) return false;
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages == null) return false;
        for (String packageName : packages) {
            if (isAllowedHostPackage(packageName)) return true;
        }
        return false;
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
                result.putBoolean("ok", false);
                result.putString("error", "JOB_START_FAILED");
                result.putString("message", e.getMessage());
            }
            return result;
        }

        if ("job_status".equals(method)) {
            String jobId = arg == null ? "" : arg;
            android.content.SharedPreferences prefs = getContext().getSharedPreferences(
                    EpicRuntimeService.PREFS, android.content.Context.MODE_PRIVATE);
            String jobState = prefs.getString(jobId + ".state", "unknown");
            long jobStartedAt = prefs.getLong(jobId + ".started_at", 0L);
            boolean startupGraceExpired = jobStartedAt > 0
                    && System.currentTimeMillis() - jobStartedAt > 30_000L;
            if ("running".equals(jobState) && startupGraceExpired
                    && !EpicRuntimeService.isJobActive(jobId)) {
                jobState = "interrupted";
                prefs.edit().putString(jobId + ".state", jobState)
                        .putLong(jobId + ".finished_at", System.currentTimeMillis()).apply();
            }
            result.putBoolean("ok", true);
            result.putString("job_id", jobId);
            result.putString("state", jobState);
            result.putString("result_json", prefs.getString(jobId + ".result", ""));
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
