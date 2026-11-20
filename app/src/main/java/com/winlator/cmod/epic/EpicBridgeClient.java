package com.winlator.cmod.epic;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class EpicBridgeClient {
    public static final String PACKAGE_NAME = "com.maliwinlator.epicbridge";
    public static final String AUTHORITY = PACKAGE_NAME + ".provider";
    private static final String ASSET_NAME = "epicbridge.apk";
    private static final int PROTOCOL_VERSION = 1;

    private EpicBridgeClient() {}

    public static boolean isInstalled(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.getPackageManager().getPackageInfo(
                        PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0));
            } else {
                context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static Bundle getStatus(Context context) {
        try {
            Bundle result = context.getContentResolver().call(
                    Uri.parse("content://" + AUTHORITY), "status", null, null);
            if (result != null) {
                if (result.getInt("protocol_version", -1) != PROTOCOL_VERSION) {
                    Bundle mismatch = new Bundle();
                    mismatch.putBoolean("installed", isInstalled(context));
                    mismatch.putBoolean("runtime_ready", false);
                    mismatch.putString("state", "protocol_mismatch");
                    mismatch.putString("message", "Epic Bridge protocol version is incompatible; reinstall the matching companion package");
                    return mismatch;
                }
                return result;
            }
            Bundle unavailable = new Bundle();
            unavailable.putBoolean("installed", isInstalled(context));
            unavailable.putBoolean("runtime_ready", false);
            unavailable.putString("state", "provider_unavailable");
            unavailable.putString("message", "Epic Bridge returned no status");
            return unavailable;
        } catch (SecurityException e) {
            Bundle result = new Bundle();
            result.putBoolean("installed", isInstalled(context));
            result.putBoolean("runtime_ready", false);
            result.putString("state", "signature_mismatch");
            result.putString("message", "Epic Bridge signature does not match this MaliWinlator build");
            return result;
        } catch (Exception e) {
            Bundle result = new Bundle();
            result.putBoolean("installed", isInstalled(context));
            result.putBoolean("runtime_ready", false);
            result.putString("state", "provider_unavailable");
            result.putString("message", "Epic Bridge provider is unavailable");
            return result;
        }
    }

    public static void requestUninstall(Context context) {
        Intent uninstall = new Intent(Intent.ACTION_DELETE);
        uninstall.setData(Uri.parse("package:" + PACKAGE_NAME));
        uninstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(uninstall);
    }

    public static boolean requestInstall(Context context) {
        if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            settings.setData(Uri.parse("package:" + context.getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
            return false;
        }

        File apk = new File(context.getCacheDir(), ASSET_NAME);
        try (InputStream input = context.getAssets().open(ASSET_NAME);
             FileOutputStream output = new FileOutputStream(apk, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.getFD().sync();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to prepare Epic Bridge installer", e);
        }

        Uri apkUri = FileProvider.getUriForFile(
                context, context.getPackageName() + ".tileprovider", apk);
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(apkUri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(install);
        return true;
    }
}
