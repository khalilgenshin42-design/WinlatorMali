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
        if (!ensureNativeLibraryLoaded()) {
            Log.e(TAG, "Runtime packaging check failed: libepic_python or a native dependency could not be loaded");
            return false;
        }
        String[] requiredAssets = {
                ASSET_ROOT + "/lib/python3.15/os.py",
                ASSET_ROOT + "/lib/python3.15/site-packages/legendary/__init__.py",
                ASSET_ROOT + "/lib/python3.15/site-packages/epicbridge_runner.py",
                ASSET_ROOT + "/lib/python3.15/zipfile/path_pkg/__init__.py",
                ASSET_ROOT + "/runtime-version.txt"
        };
        for (String asset : requiredAssets) {
            try (InputStream ignored = context.getAssets().open(asset)) {
                // Opening every required entry identifies omissions in the final APK.
            } catch (IOException e) {
                Log.e(TAG, "Runtime packaging check: missing APK asset " + asset, e);
                return false;
            }
        }
        Log.i(TAG, "Runtime packaging check passed; nativeLibraryDir="
                + context.getApplicationInfo().nativeLibraryDir);
        return true;
    }

    public static synchronized boolean initialize(Context context) {
        if (initialized) return true;
        if (!isPackaged(context)) return false;

        File runtimeRoot = new File(context.getFilesDir(), "epic-runtime");
        File pythonHome = new File(runtimeRoot, ASSET_ROOT);
        File marker = new File(runtimeRoot, RUNTIME_MARKER);
        final String packagedVersion;
        try (InputStream versionInput = context.getAssets().open(ASSET_ROOT + "/runtime-version.txt")) {
            packagedVersion = readUtf8(versionInput).trim();
        } catch (IOException e) {
            Log.e(TAG, "The packaged Python runtime has no build identity", e);
            return false;
        }
        if (!packagedVersion.matches("[0-9a-f]{64}")) {
            Log.e(TAG, "The packaged Python runtime identity is missing or malformed");
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
        File pythonLib = new File(pythonHome, "lib/python3.15");
        File sitePackages = new File(pythonLib, "site-packages");
        File extensionDir = new File(pythonLib, "lib-dynload");
        File[] extensionFiles = extensionDir.listFiles();
        boolean runtimeExtensionsExecutable = extensionDir.isDirectory()
                && extensionFiles != null && sharedLibrariesExecutable(pythonLib);
        boolean runtimeFilesPresent = runtimeExtensionsExecutable
                && new File(pythonLib, "os.py").isFile()
                && new File(pythonLib, "zipfile/__init__.py").isFile()
                && new File(pythonLib, "zipfile/_path/__init__.py").isFile()
                && new File(sitePackages, "epicbridge_runner.py").isFile()
                && new File(sitePackages, "legendary/__init__.py").isFile()
                && new File(sitePackages, "legendary/android_crypto.py").isFile()
                && new File(sitePackages, "requests/__init__.py").isFile()
                && new File(sitePackages, "certifi/cacert.pem").isFile()
                && new File(sitePackages, "filelock/__init__.py").isFile();
        if (!packagedVersion.equals(installedVersion) || !runtimeFilesPresent) {
            // Runtime assets are content-addressed. Updates to Python, Legendary,
            // or the bridge runner replace stale extracted files without touching HOME.
            if (!deleteRecursively(pythonHome)) {
                Log.e(TAG, "Unable to remove the previous incomplete Python runtime");
                return false;
            }
            if (!runtimeRoot.mkdirs() && !runtimeRoot.isDirectory()) {
                Log.e(TAG, "Unable to create runtime directory");
                return false;
            }
            try {
                extractAssetDirectory(context.getAssets(), ASSET_ROOT, runtimeRoot);
                File extractedPythonLib = new File(pythonHome, "lib/python3.15");
                // Android asset merging can omit the underscore-prefixed CPython
                // zipfile/_path directory. Package it under a neutral asset name,
                // then restore CPython's exact import path in app-private storage.
                File zipfileDir = new File(extractedPythonLib, "zipfile");
                File stagedZipPath = new File(zipfileDir, "path_pkg");
                File actualZipPath = new File(zipfileDir, "_path");
                if (!actualZipPath.isDirectory() && stagedZipPath.isDirectory()
                        && !stagedZipPath.renameTo(actualZipPath)) {
                    throw new IOException("Unable to restore CPython zipfile/_path package");
                }
                File extractedSitePackages = new File(extractedPythonLib, "site-packages");
                File extractedExtensionDir = new File(extractedPythonLib, "lib-dynload");
                String missingRuntimeFile = firstMissingRuntimeFile(extractedPythonLib, extractedSitePackages);
                if (missingRuntimeFile != null) {
                    throw new IOException("Extracted CPython runtime is missing required file: " + missingRuntimeFile);
                }
                if (!extractedExtensionDir.isDirectory() || extractedExtensionDir.listFiles() == null) {
                    throw new IOException("CPython extension directory is missing or unreadable: " + extractedExtensionDir);
                }
                makeSharedLibrariesExecutable(extractedPythonLib);
                String nonExecutableLibrary = firstNonExecutableSharedLibrary(extractedPythonLib);
                if (nonExecutableLibrary != null) {
                    throw new IOException("CPython shared library is not executable after chmod: " + nonExecutableLibrary);
                }
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
            Log.e(TAG, "Unable to configure Python HOME/TMPDIR", e);
            return false;
        }

        if (!ensureNativeLibraryLoaded()) return false;
        initialized = nativeInitialize(pythonHome.getAbsolutePath(), tempDir.getAbsolutePath());
        return initialized;
    }

    public static synchronized String call(Context context, String command, String payloadJson) {
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

            // AssetManager can expose directory entries through list() even on
            // Android builds where open(directory) does not reliably throw.
            // Resolve non-empty directories before attempting to open a file;
            // otherwise a directory may be copied as an empty file and its
            // descendants silently disappear from the extracted runtime.
            String[] nested = assets.list(childAsset);
            if (nested == null) {
                throw new IOException("Unable to inspect packaged runtime asset " + childAsset);
            }
            if (nested.length > 0) {
                extractAssetDirectory(assets, childAsset, targetRoot);
                continue;
            }

            final InputStream assetInput;
            try {
                assetInput = assets.open(childAsset);
            } catch (FileNotFoundException e) {
                // An empty AssetManager listing plus an unopenable entry is an
                // empty directory, which still needs to exist in app storage.
                if (!target.mkdirs() && !target.isDirectory()) {
                    throw new IOException("Unable to create empty runtime directory " + target, e);
                }
                continue;
            }
            try (InputStream input = assetInput;
                 FileOutputStream output = new FileOutputStream(target, false)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            // CPython loads lib-dynload extension modules with dlopen().
            // Keep extracted .so files executable inside app-private storage.
            if (target.isFile() && child.matches(".*\\.so(?:\\..*)?$")
                    && !target.canExecute() && !target.setExecutable(true, true)) {
                throw new IOException("Unable to mark Python extension executable: " + target);
            }
        }
    }

    private static String firstMissingRuntimeFile(File pythonLib, File sitePackages) {
        String[] required = {
                new File(pythonLib, "os.py").getAbsolutePath(),
                new File(pythonLib, "zipfile/__init__.py").getAbsolutePath(),
                new File(pythonLib, "zipfile/_path/__init__.py").getAbsolutePath(),
                new File(sitePackages, "legendary/__init__.py").getAbsolutePath(),
                new File(sitePackages, "legendary/android_crypto.py").getAbsolutePath(),
                new File(sitePackages, "epicbridge_runner.py").getAbsolutePath()
        };
        for (String path : required) if (!new File(path).isFile()) return path;
        return null;
    }

    private static void makeSharedLibrariesExecutable(File directory) throws IOException {
        if (directory == null || !directory.isDirectory()) {
            throw new IOException("Runtime directory is missing: " + directory);
        }
        File[] children = directory.listFiles();
        if (children == null) throw new IOException("Unable to list runtime directory: " + directory);
        for (File child : children) {
            if (child.isDirectory()) {
                makeSharedLibrariesExecutable(child);
            } else if (child.isFile() && child.getName().matches(".*\\.so(?:\\..*)?$")
                    && !child.canExecute() && !child.setExecutable(true, true)) {
                throw new IOException("Unable to set executable permission on CPython shared library: "
                        + child.getAbsolutePath());
            }
        }
    }

    private static String firstNonExecutableSharedLibrary(File directory) {
        if (directory == null || !directory.isDirectory()) return directory == null ? "null" : directory.getAbsolutePath();
        File[] children = directory.listFiles();
        if (children == null) return directory.getAbsolutePath() + " (unreadable directory)";
        for (File child : children) {
            if (child.isDirectory()) {
                String nested = firstNonExecutableSharedLibrary(child);
                if (nested != null) return nested;
            } else if (child.isFile() && child.getName().matches(".*\\.so(?:\\..*)?$")
                    && !child.canExecute()) {
                return child.getAbsolutePath();
            }
        }
        return null;
    }

    private static boolean sharedLibrariesExecutable(File directory) {
        return firstNonExecutableSharedLibrary(directory) == null;
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                Log.w(TAG, "Unable to list runtime directory for deletion: " + file);
                return false;
            }
            for (File child : children) {
                if (!deleteRecursively(child)) return false;
            }
        }
        if (!file.delete() && file.exists()) {
            Log.w(TAG, "Unable to delete " + file);
            return false;
        }
        return true;
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

    static long acquireJobLock(String path) {
        if (!ensureNativeLibraryLoaded()) {
            throw new IllegalStateException("Epic native lock support is unavailable");
        }
        long descriptor = nativeAcquireJobLock(path);
        if (descriptor < 0) throw new IllegalStateException("Unable to acquire Epic job lock");
        return descriptor;
    }

    static void releaseJobLock(long descriptor) {
        nativeReleaseJobLock(descriptor);
    }

    private static native long nativeAcquireJobLock(String path);
    private static native void nativeReleaseJobLock(long descriptor);
    private static native boolean nativeInitialize(String pythonHome, String tempDir);
    private static native String nativeCall(String command, String payloadJson);
}
