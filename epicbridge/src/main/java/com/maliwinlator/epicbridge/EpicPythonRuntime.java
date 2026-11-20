package com.maliwinlator.epicbridge;

import android.content.Context;
import android.content.res.AssetManager;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

public final class EpicPythonRuntime {
    private static final String TAG = "EpicPythonRuntime";
    private static final String ASSET_ROOT = "python";
    private static final String RUNTIME_MARKER = ".maliwinlator-runtime";
    private static volatile boolean initialized;
    private static volatile boolean nativeLibraryLoaded;

    private EpicPythonRuntime() {}

    public static boolean isPackaged(Context context) {
        if (!ensureNativeLibraryLoaded()) return false;
        try (InputStream ignored = context.getAssets().open(
                    ASSET_ROOT + "/lib/python3.15/os.py");
             InputStream legendary = context.getAssets().open(
                    ASSET_ROOT + "/lib/python3.15/site-packages/legendary/__init__.py");
             InputStream runner = context.getAssets().open(
                    ASSET_ROOT + "/lib/python3.15/site-packages/epicbridge_runner.py")) {
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
        final String packagedVersion;
        try (InputStream versionInput = context.getAssets().open(ASSET_ROOT + "/.runtime-version")) {
            packagedVersion = readUtf8(versionInput).trim();
        } catch (IOException e) {
            Log.e(TAG, "The packaged Python runtime has no build identity", e);
            return false;
        }
        if (packagedVersion.isEmpty()) {
            Log.e(TAG, "The packaged Python runtime identity is empty");
            return false;
        }

        String installedVersion = "";
        if (marker.isFile()) {
            try (InputStream markerInput = new FileInputStream(marker)) {
                installedVersion = readUtf8(markerInput).trim();
            } catch (IOException e) {
                Log.w(TAG, "Unable to read the installed runtime marker; refreshing runtime", e);
            }
        }
        boolean runtimeFilesPresent =
                new File(pythonHome, "lib/python3.15/os.py").isFile()
                && new File(pythonHome, "lib/python3.15/site-packages/epicbridge_runner.py").isFile();
        if (!packagedVersion.equals(installedVersion) || !runtimeFilesPresent) {
            // Runtime assets are content-addressed. Updates to Python, Legendary,
            // or the bridge runner replace stale extracted files without touching HOME.
            deleteRecursively(pythonHome);
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
                File homeDir = new File(runtimeRoot, "home");
                if (!homeDir.mkdirs() && !homeDir.isDirectory()) {
                    throw new IOException("Unable to create Legendary data directory");
                }
                File markerTemp = new File(runtimeRoot, RUNTIME_MARKER + ".tmp");
                try (FileOutputStream output = new FileOutputStream(markerTemp, false)) {
                    output.write(packagedVersion.getBytes("UTF-8"));
                    output.write('\n');
                    output.getFD().sync();
                }
                if (marker.exists() && !marker.delete()) {
                    throw new IOException("Unable to replace old runtime marker");
                }
                if (!markerTemp.renameTo(marker)) {
                    throw new IOException("Unable to commit runtime marker");
                }
            } catch (IOException e) {
                Log.e(TAG, "Unable to extract the bundled CPython runtime", e);
                deleteRecursively(pythonHome);
                marker.delete();
                new File(runtimeRoot, RUNTIME_MARKER + ".tmp").delete();
                return false;
            }
        }

        File tempDir = context.getCacheDir();
        File homeDir = new File(runtimeRoot, "home");
        if (!homeDir.mkdirs() && !homeDir.isDirectory()) {
            Log.e(TAG, "Unable to create Legendary data directory");
            return false;
        }
        try {
            Os.setenv("TMPDIR", tempDir.getAbsolutePath(), true);
            Os.setenv("HOME", homeDir.getAbsolutePath(), true);
        } catch (Exception e) {
            Log.w(TAG, "Unable to set optional Python environment variables", e);
        }

        if (!ensureNativeLibraryLoaded()) return false;
        initialized = nativeInitialize(pythonHome.getAbsolutePath(), tempDir.getAbsolutePath());
        return initialized;
    }

    public static String call(Context context, String command, String payloadJson) {
        if (!initialize(context)) {
            return "{\"ok\":false,\"error\":\"RUNTIME_NOT_READY\",\"message\":\"The bundled Android CPython runtime is unavailable\"}";
        }
        return nativeCall(command, payloadJson == null ? "{}" : payloadJson);
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toString("UTF-8");
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
                }
            } catch (FileNotFoundException e) {
                String[] nested = assets.list(childAsset);
                if (nested == null || nested.length == 0) {
                    throw new IOException("Missing packaged runtime asset " + childAsset, e);
                }
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

    private static synchronized boolean ensureNativeLibraryLoaded() {
        if (nativeLibraryLoaded) return true;
        try {
            System.loadLibrary("epic_python");
            nativeLibraryLoaded = true;
            return true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "CPython JNI library or one of its dependencies is missing", e);
            return false;
        }
    }

    private static native boolean nativeInitialize(String pythonHome, String tempDir);
    private static native String nativeCall(String command, String payloadJson);
}
