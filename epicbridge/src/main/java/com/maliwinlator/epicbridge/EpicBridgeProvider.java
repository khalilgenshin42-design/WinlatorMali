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
    private static volatile Boolean cryptoSelfTest;

    @Override
    public boolean onCreate() {
        return true;
    }

    private boolean isAuthorizedHost() {
        if (getContext() == null) return false;
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages == null) return false;
        for (String packageName : packages) {
            if ("com.maliwinlator.app".equals(packageName)) return true;
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
