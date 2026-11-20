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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class EpicRuntimeService extends Service {
    public static final String ACTION_RUN_JOB = "com.maliwinlator.epicbridge.RUN_JOB";
    public static final String EXTRA_JOB_ID = "job_id";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_PAYLOAD = "payload_json";
    public static final String PREFS = "epic_runtime_jobs";
    private static final String CHANNEL_ID = "epic_runtime_jobs";
    private static final String TAG = "EpicRuntimeService";
    private static final int FOREGROUND_NOTIFICATION_ID = 0x45504943;

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

        if (activeJobs.getAndIncrement() == 0) {
            startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification("install".equals(command) ? "Installing Epic game" : "Uninstalling Epic game", true));
            if (!wakeLock.isHeld()) wakeLock.acquire(6L * 60L * 60L * 1000L);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(jobId + ".state", "running")
                .putString(jobId + ".result", "")
                .putLong(jobId + ".started_at", System.currentTimeMillis())
                .apply();

        executor.execute(() -> {
            String response;
            try {
                response = EpicPythonRuntime.call(this, command, payload == null ? "{}" : payload);
            } catch (Throwable error) {
                Log.e(TAG, "Epic install job failed", error);
                JSONObject failure = new JSONObject();
                try {
                    failure.put("ok", false);
                    failure.put("error", error.getClass().getSimpleName());
                    failure.put("message", error.getMessage() == null ? "Epic install failed" : error.getMessage());
                } catch (Exception ignored) {}
                response = failure.toString();
            }

            boolean success = false;
            try { success = new JSONObject(response).optBoolean("ok", false); }
            catch (Exception ignored) {}
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(jobId + ".state", success ? "complete" : "failed")
                    .putString(jobId + ".result", response)
                    .putLong(jobId + ".finished_at", System.currentTimeMillis())
                    .apply();

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
        executor.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
