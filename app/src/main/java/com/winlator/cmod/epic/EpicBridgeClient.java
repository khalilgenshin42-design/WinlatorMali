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
    private static final int PROTOCOL_VERSION = 2;

    // The runtime is packaged in the host APK and exposed only to the same UID.
    // Keep the separately installed companion authority as a migration fallback.
    private static String embeddedAuthority(Context context) {
        return context.getPackageName() + ".epicprovider";
    }

    private static Bundle callAuthority(Context context, String authority,
            String method, String arg, Bundle extras) {
        return context.getContentResolver().call(
                Uri.parse("content://" + authority), method, arg, extras);
    }

    private static Bundle callEmbedded(Context context, String method,
            String arg, Bundle extras) {
        try {
            Bundle result = callAuthority(context, embeddedAuthority(context), method, arg, extras);
            if (result != null && result.getInt("protocol_version", -1) == PROTOCOL_VERSION) {
                return result;
            }
        } catch (Exception ignored) {
            // An older host build may not include the in-package provider yet.
        }
        return null;
    }

    private EpicBridgeClient() {}

    public static boolean isInstalled(Context context) {
        Bundle embedded = callEmbedded(context, "status", null, null);
        if (embedded != null && embedded.getBoolean("installed", false)) return true;
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

    private static boolean isCompanionInstalled(Context context) {
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

    private static boolean shouldFallbackToCompanion(Bundle embedded, boolean statusCall) {
        if (embedded == null) return true;
        if (statusCall) {
            // A host APK may contain the provider class but lack a usable runtime
            // payload after an interrupted/partial migration. Do not let that
            // dead-end hide a healthy separately installed companion.
            String state = embedded.getString("state", "");
            return !embedded.getBoolean("runtime_ready", false)
                    && ("runtime_not_installed".equals(state)
                    || "provider_unavailable".equals(state));
        }
        String error = embedded.getString("error", "");
        return "RUNTIME_NOT_READY".equals(error)
                || "PROVIDER_UNAVAILABLE".equals(error);
    }

    public static Bundle getStatus(Context context) {
        try {
            Bundle result = callEmbedded(context, "status", null, null);
            if (shouldFallbackToCompanion(result, true) && isCompanionInstalled(context)) {
                result = callAuthority(context, AUTHORITY, "status", null, null);
            }
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

    public static Bundle call(Context context, String method, String payloadJson) {
        return call(context, method, null, payloadJson);
    }

    private static final String JOB_PREFS = "epic_bridge_jobs";
    private static final String JOB_AUTHORITY_SUFFIX = ".provider_authority";

    private static String jobAuthorityKey(String jobId) {
        return jobId + JOB_AUTHORITY_SUFFIX;
    }

    private static String alternateAuthority(Context context, String current) {
        return AUTHORITY.equals(current) ? embeddedAuthority(context) : AUTHORITY;
    }

    private static Bundle tryCallAuthority(Context context, String authority,
            String method, String arg, Bundle extras) {
        try {
            return callAuthority(context, authority, method, arg, extras);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isCompatible(Bundle result) {
        return result != null
                && result.getInt("protocol_version", -1) == PROTOCOL_VERSION;
    }

    private static boolean isProviderUnavailable(Bundle result) {
        if (result == null) return true;
        String error = result.getString("error", "");
        return "PROVIDER_UNAVAILABLE".equals(error)
                || "PROVIDER_CALL_FAILED".equals(error)
                || "RUNTIME_NOT_READY".equals(error);
    }

    private static boolean isUnknownJobStatus(Bundle result) {
        return isCompatible(result) && result.getBoolean("ok", false)
                && "unknown".equals(result.getString("state", "unknown"));
    }

    private static boolean isKnownJobStatus(Bundle result) {
        if (!isCompatible(result) || !result.getBoolean("ok", false)) return false;
        String state = result.getString("state", "");
        return !state.isEmpty() && !"unknown".equals(state);
    }

    private static boolean isTerminalJobStatus(Bundle result) {
        if (!isKnownJobStatus(result)) return false;
        String state = result.getString("state", "");
        return "complete".equals(state) || "failed".equals(state)
                || "cancelled".equals(state) || "interrupted".equals(state);
    }

    public static Bundle call(Context context, String method, String arg, String payloadJson) {
        Bundle extras = new Bundle();
        extras.putString("payload_json", payloadJson == null ? "{}" : payloadJson);
        try {
            String embedded = embeddedAuthority(context);
            String jobId = arg == null ? "" : arg;
            boolean jobStatusCall = "job_status".equals(method);
            boolean cancelJobCall = "cancel_job".equals(method);
            boolean jobScopedCall = jobStatusCall || cancelJobCall;
            android.content.SharedPreferences jobPrefs = context.getSharedPreferences(
                    JOB_PREFS, Context.MODE_PRIVATE);
            String authorityKey = jobId.isEmpty() ? "" : jobAuthorityKey(jobId);
            String savedAuthority = jobScopedCall && !authorityKey.isEmpty()
                    ? jobPrefs.getString(authorityKey, "") : "";

            Bundle result = null;
            String selectedAuthority = embedded;

            // Keep every status/cancel request on the same provider that accepted
            // start_job. The embedded runtime and separately installed companion
            // have independent SharedPreferences and independent service processes.
            if (!savedAuthority.isEmpty()) {
                result = tryCallAuthority(context, savedAuthority, method, arg, extras);
                selectedAuthority = savedAuthority;
                if (result == null || !isCompatible(result) || isProviderUnavailable(result)
                        || (jobStatusCall && isUnknownJobStatus(result))) {
                    String alternate = alternateAuthority(context, savedAuthority);
                    if (!AUTHORITY.equals(alternate) || isCompanionInstalled(context)) {
                        Bundle alternateResult = tryCallAuthority(
                                context, alternate, method, arg, extras);
                        boolean usefulAlternate = jobStatusCall
                                ? isKnownJobStatus(alternateResult)
                                : isCompatible(alternateResult) && !isProviderUnavailable(alternateResult);
                        if (usefulAlternate) {
                            result = alternateResult;
                            selectedAuthority = alternate;
                            jobPrefs.edit().putString(authorityKey, alternate).apply();
                        }
                    }
                }
            } else if (cancelJobCall && !jobId.isEmpty()) {
                // Recover cancellation routing for jobs created before provider
                // affinity was persisted by probing each provider's job registry.
                Bundle embeddedStatus = tryCallAuthority(
                        context, embedded, "job_status", jobId, extras);
                if (isKnownJobStatus(embeddedStatus)) {
                    result = tryCallAuthority(context, embedded, method, arg, extras);
                    selectedAuthority = embedded;
                } else if (isCompanionInstalled(context)) {
                    Bundle companionStatus = tryCallAuthority(
                            context, AUTHORITY, "job_status", jobId, extras);
                    if (isKnownJobStatus(companionStatus)) {
                        result = tryCallAuthority(context, AUTHORITY, method, arg, extras);
                        selectedAuthority = AUTHORITY;
                        jobPrefs.edit().putString(authorityKey, AUTHORITY).apply();
                    }
                }
            }

            if (result == null) {
                result = callEmbedded(context, method, arg, extras);
                selectedAuthority = embedded;
            }
            if (embedded.equals(selectedAuthority)
                    && shouldFallbackToCompanion(result, false)
                    && isCompanionInstalled(context)) {
                result = tryCallAuthority(context, AUTHORITY, method, arg, extras);
                selectedAuthority = AUTHORITY;
            }

            // Older jobs may predate provider-affinity storage. If the embedded
            // provider answers "unknown", probe the companion before concluding
            // that the saved job is missing.
            if (jobStatusCall && savedAuthority.isEmpty()
                    && isUnknownJobStatus(result) && isCompanionInstalled(context)) {
                Bundle companionStatus = tryCallAuthority(
                        context, AUTHORITY, method, arg, extras);
                if (isKnownJobStatus(companionStatus)) {
                    result = companionStatus;
                    selectedAuthority = AUTHORITY;
                }
            }

            if (result == null) {
                Bundle unavailable = new Bundle();
                unavailable.putBoolean("ok", false);
                unavailable.putString("error", "PROVIDER_UNAVAILABLE");
                return unavailable;
            }
            if (!isCompatible(result)) {
                Bundle mismatch = new Bundle();
                mismatch.putBoolean("ok", false);
                mismatch.putString("error", "PROTOCOL_MISMATCH");
                mismatch.putString("message", "Reinstall the matching Epic Bridge companion package");
                return mismatch;
            }

            if ("start_job".equals(method) && result.getBoolean("ok", false)) {
                String startedJobId = result.getString("job_id", "");
                if (!startedJobId.isEmpty()) {
                    jobPrefs.edit().putString(
                            jobAuthorityKey(startedJobId), selectedAuthority).commit();
                }
            } else if (jobStatusCall && !authorityKey.isEmpty() && isKnownJobStatus(result)) {
                jobPrefs.edit().putString(authorityKey, selectedAuthority).apply();
                if (isTerminalJobStatus(result)) {
                    jobPrefs.edit().remove(authorityKey).apply();
                }
            }
            return result;
        } catch (Exception e) {
            Bundle unavailable = new Bundle();
            unavailable.putBoolean("ok", false);
            unavailable.putString("error", "PROVIDER_CALL_FAILED");
            unavailable.putString("message", e.getMessage());
            return unavailable;
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
