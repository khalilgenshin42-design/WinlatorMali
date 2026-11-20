package com.maliwinlator.epicbridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;

public final class EpicBridgeProvider extends ContentProvider {
    public static final String AUTHORITY = "com.maliwinlator.epicbridge.provider";
    public static final int PROTOCOL_VERSION = 1;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        result.putInt("protocol_version", PROTOCOL_VERSION);

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
