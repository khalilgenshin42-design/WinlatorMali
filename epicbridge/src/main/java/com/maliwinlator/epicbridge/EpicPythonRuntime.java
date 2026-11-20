package com.maliwinlator.epicbridge;

import android.content.Context;
import android.content.res.AssetManager;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

public final class EpicPythonRuntime {
    private static final String TAG = "EpicPythonRuntime";
    private static final String ASSET_ROOT = "python";
    private static final String RUNTIME_MARKER = ".maliwinlator-runtime-3.15.2-legendary-0.21.1";
    private static volatile boolean initialized;

    private EpicPythonRuntime() {}

    public static boolean isPackaged(Context context) {
        File nativeLibrary = new File(context.getApplicationInfo().nativeLibraryDir, "libepic_python.so");
        if (!nativeLibrary.isFile()) return false;
        try (InputStream ignored = context.getAssets().open(
                ASSET_ROOT + "/lib/python3.15/os.py")) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static synchronized boolean initialize(Context context) {
        if (initialized) return true;
        if (!isPackaged(context)) return false;

        File runtimeRoot = new File(context.getFilesDir(), "epic-runtime");
        File pythonHome = new File(runtimeRoot, ASSET_ROOT);
        File marker = new File(runtimeRoot, RUNTIME_MARKER);
        if (!marker.isFile()) {
            deleteRecursively(runtimeRoot);
            if (!runtimeRoot.mkdirs() && !runtimeRoot.isDirectory()) {
                Log.e(TAG, "Unable to create runtime directory");
                return false;
            }
            try {
                extractAssetDirectory(context.getAssets(), ASSET_ROOT, runtimeRoot);
                File cwd = new File(pythonHome, "cwd");
                if (!cwd.mkdirs() && !cwd.isDirectory()) {
                    throw new IOException("Unable to create Python working directory");
                }
                if (!marker.createNewFile()) {
                    throw new IOException("Unable to write runtime marker");
                }
            } catch (IOException e) {
                Log.e(TAG, "Unable to extract the bundled CPython runtime", e);
                deleteRecursively(runtimeRoot);
                return false;
            }
        }

        File tempDir = context.getCacheDir();
        try {
            Os.setenv("TMPDIR", tempDir.getAbsolutePath(), true);
            Os.setenv("HOME", new File(runtimeRoot, "home").getAbsolutePath(), true);
        } catch (Exception e) {
            Log.w(TAG, "Unable to set optional Python environment variables", e);
        }

        try {
            System.loadLibrary("epic_python");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "CPython JNI library is missing", e);
            return false;
        }

        initialized = nativeInitialize(pythonHome.getAbsolutePath(), tempDir.getAbsolutePath());
        return initialized;
    }

    public static String call(Context context, String command, String payloadJson) {
        if (!initialize(context)) {
            return "{\"ok\":false,\"error\":\"RUNTIME_NOT_READY\",\"message\":\"The bundled Android CPython runtime is unavailable\"}";
        }
        return nativeCall(command, payloadJson == null ? "{}" : payloadJson);
    }

    private static void extractAssetDirectory(AssetManager assets, String assetPath, File targetRoot)
            throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null) throw new IOException("Unable to list asset directory " + assetPath);
        File targetDir = new File(targetRoot, assetPath);
        if (!targetDir.mkdirs() && !targetDir.isDirectory()) {
            throw new IOException("Unable to create " + targetDir);
        }

        for (String child : children) {
            File target = new File(targetDir, child);
            String childAsset = assetPath + "/" + child;
            try (InputStream input = assets.open(childAsset)) {
                try (FileOutputStream output = new FileOutputStream(target, false)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    output.getFD().sync();
                }
            } catch (FileNotFoundException e) {
                extractAssetDirectory(assets, childAsset, targetRoot);
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        if (!file.delete()) Log.w(TAG, "Unable to delete " + file);
    }

    private static native boolean nativeInitialize(String pythonHome, String tempDir);
    private static native String nativeCall(String command, String payloadJson);
}
