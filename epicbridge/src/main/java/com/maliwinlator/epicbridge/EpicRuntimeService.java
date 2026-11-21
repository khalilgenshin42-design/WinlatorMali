package com.maliwinlator.epicbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EpicRuntimeService extends Service {
    public static final String ACTION_RUN_JOB = "com.maliwinlator.epicbridge.RUN_JOB";
    public static final String EXTRA_JOB_ID = "job_id";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_PAYLOAD = "payload_json";
    public static final String PREFS = "epic_runtime_jobs";
    private static final String CHANNEL_ID = "epic_runtime_jobs";
    private static final String TAG = "EpicRuntimeService";
    private static final int FOREGROUND_NOTIFICATION_ID = 0x45504943;
    private static final Set<String> ACTIVE_JOB_IDS = ConcurrentHashMap.newKeySet();

    public static boolean isJobActive(String jobId) {
        return jobId != null && ACTIVE_JOB_IDS.contains(jobId);
    }

    /**
     * Atomically reserve the single Epic install slot before the provider starts
     * the service. This closes the window where two rapid host requests could
     * both be accepted before either onStartCommand() has run.
     */
    public static synchronized boolean reserveJob(String jobId) {
        return jobId != null && !jobId.isEmpty()
                && ACTIVE_JOB_IDS.isEmpty() && ACTIVE_JOB_IDS.add(jobId);
    }

    public static synchronized void releaseJobReservation(String jobId) {
        if (jobId != null) ACTIVE_JOB_IDS.remove(jobId);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger activeJobs = new AtomicInteger();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();
        PowerManager powerManager = getSystemService(PowerManager.class);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getPackageName() + ":EpicDownload");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_RUN_JOB.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        String jobId = intent.getStringExtra(EXTRA_JOB_ID);
        String command = intent.getStringExtra(EXTRA_COMMAND);
        String payload = intent.getStringExtra(EXTRA_PAYLOAD);
        if (jobId == null || command == null || (!"install".equals(command) && !"uninstall".equals(command))) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        final String jobPayload;
        final File controlFile;
        final File statusFile;
        try {
            File jobsDirectory = new File(getFilesDir(), "epic_jobs");
            if (!jobsDirectory.isDirectory() && !jobsDirectory.mkdirs()) {
                throw new IllegalStateException("Unable to create private job directory");
            }
            controlFile = new File(jobsDirectory, jobId + ".control.json");
            statusFile = new File(jobsDirectory, jobId + ".status.json");
            writePrivateFile(controlFile, "{\"cancel\":false}");
            writePrivateFile(statusFile, "{\"state\":\"queued\",\"phase\":\"preparing\"}");
            JSONObject payloadObject = new JSONObject(payload == null ? "{}" : payload);
            if ("install".equals(command)) {
                payloadObject.put("job_control_path", controlFile.getAbsolutePath());
                payloadObject.put("job_status_path", statusFile.getAbsolutePath());
            }
            jobPayload = payloadObject.toString();
        } catch (Exception invalidPayload) {
            EpicRuntimeService.releaseJobReservation(jobId);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (activeJobs.getAndIncrement() == 0) {
            startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification("install".equals(command) ? "Installing Epic game" : "Uninstalling Epic game", true));
            if (!wakeLock.isHeld()) wakeLock.acquire(6L * 60L * 60L * 1000L);
        }
        ACTIVE_JOB_IDS.add(jobId);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(jobId + ".state", "running")
                .putString(jobId + ".result", "")
                .putString(jobId + ".command", command)
                .putString(jobId + ".control_path", controlFile.getAbsolutePath())
                .putString(jobId + ".status_path", statusFile.getAbsolutePath())
                .putLong(jobId + ".started_at", System.currentTimeMillis())
                .commit();

        executor.execute(() -> {
            String response;
            try {
                response = EpicPythonRuntime.call(this, command, jobPayload);
            } catch (Throwable error) {
                // Throwable messages and stack traces may contain private paths
                // or sensitive request details; persist only a stable public error.
                Log.e(TAG, "Epic job failed (exception=" + error.getClass().getSimpleName() + ")");
                JSONObject failure = new JSONObject();
                try {
                    failure.put("ok", false);
                    failure.put("error", "EPIC_OPERATION_FAILED");
                    failure.put("message", "Epic operation failed. Review the app diagnostic log.");
                } catch (Exception ignored) {}
                response = failure.toString();
            }

            JSONObject responseObject;
            try {
                responseObject = new JSONObject(response == null ? "" : response);
            } catch (Exception invalidResponse) {
                responseObject = new JSONObject();
                try {
                    responseObject.put("ok", false);
                    responseObject.put("message", "Epic runtime returned an empty or invalid job response");
                } catch (Exception ignored) {}
                response = responseObject.toString();
            }
            boolean cancelled = responseObject.optBoolean("cancelled", false);
            boolean success = responseObject.optBoolean("ok", false);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(jobId + ".state", cancelled ? "cancelled" : (success ? "complete" : "failed"))
                    .putString(jobId + ".result", response)
                    .putLong(jobId + ".finished_at", System.currentTimeMillis())
                    .commit();
            ACTIVE_JOB_IDS.remove(jobId);

            int remaining = activeJobs.decrementAndGet();
            if (remaining <= 0) {
                mainHandler.post(() -> {
                    // A new start may arrive after decrementAndGet() but before this
                    // callback reaches the main thread. Re-check before hiding the FGS.
                    if (activeJobs.get() == 0) {
                        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                        stopForeground(STOP_FOREGROUND_REMOVE);
                        stopSelfResult(startId);
                    } else {
                        notificationManager.notify(FOREGROUND_NOTIFICATION_ID,
                                buildNotification("Another Epic download is running", true));
                    }
                });
            } else {
                notificationManager.notify(FOREGROUND_NOTIFICATION_ID,
                        buildNotification("Another Epic download is running", true));
            }
        });
        return START_NOT_STICKY;
    }

    private static void writePrivateFile(File file, String contents) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }

    private int notificationId(String jobId) {
        int value = jobId.hashCode() & 0x7fffffff;
        return value == 0 ? 1 : value;
    }

    private Notification buildNotification(String message, boolean ongoing) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("MaliWinlator Epic Games")
                .setContentText(message)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Epic game downloads", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Progress and completion status for Epic game downloads");
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        for (String jobId : ACTIVE_JOB_IDS) {
            if ("running".equals(prefs.getString(jobId + ".state", "unknown"))) {
                prefs.edit().putString(jobId + ".state", "interrupted")
                        .putLong(jobId + ".finished_at", System.currentTimeMillis()).commit();
            }
        }
        ACTIVE_JOB_IDS.clear();
        executor.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
