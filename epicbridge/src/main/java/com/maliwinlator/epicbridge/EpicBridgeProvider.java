package com.maliwinlator.epicbridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Binder;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

public final class EpicBridgeProvider extends ContentProvider {
    public static final String AUTHORITY = "com.maliwinlator.epicbridge.provider";
    public static final int PROTOCOL_VERSION = 1;

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
            result.putBoolean("installed", true);
            result.putBoolean("runtime_ready", false);
            result.putBoolean("storage_access",
                    Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager());
            result.putString("state", "runtime_not_installed");
            result.putString("message", "Epic runtime bootstrap is not installed yet");
            return result;
        }

        result.putBoolean("ok", false);
        result.putString("error", "RUNTIME_NOT_READY");
        result.putString("message", "Epic runtime is not installed in this companion build");
        return result;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
