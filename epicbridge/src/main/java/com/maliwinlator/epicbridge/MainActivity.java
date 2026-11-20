package com.maliwinlator.epicbridge;

import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private TextView statusView;
    private TextView noticeView;
    private Button selfTestButton;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("MaliWinlator Epic Bridge");
        title.setTextSize(24);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setPadding(0, pad, 0, pad);
        root.addView(statusView);

        Button storageButton = new Button(this);
        storageButton.setText("Grant game-library storage access");
        storageButton.setOnClickListener(v -> requestStorageAccess());
        root.addView(storageButton);

        noticeView = new TextView(this);
        noticeView.setPadding(0, pad, 0, 0);
        root.addView(noticeView);

        selfTestButton = new Button(this);
        selfTestButton.setText("Validate Epic runtime");
        selfTestButton.setOnClickListener(v -> runRuntimeSelfTest());
        root.addView(selfTestButton);

        setContentView(root);
        refreshStatus();
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private boolean hasStorageAccess() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    private void refreshStatus() {
        boolean runtimePackaged = EpicPythonRuntime.isPackaged(this);
        selfTestButton.setVisibility(runtimePackaged ? Button.VISIBLE : Button.GONE);
        statusView.setText((hasStorageAccess()
                ? "Shared game-library access: granted"
                : "Shared game-library access: required")
                + "\nCPython / Legendary runtime: "
                + (runtimePackaged ? "bundled" : "not bundled"));
        noticeView.setText(runtimePackaged
                ? "Epic account data and Legendary run in this separate companion package. Return to MaliWinlator to sign in and manage your Epic library."
                : "This is a bootstrap companion build. Epic login, library, downloads and launch features become available when the Android CPython/Legendary runtime is bundled.");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1002);
        }
    }

    private void runRuntimeSelfTest() {
        selfTestButton.setEnabled(false);
        statusView.setText("Initializing CPython and validating Legendary, Android AES-GCM and downloader threads...");
        io.execute(() -> {
            String output = EpicPythonRuntime.call(this, "runtime_self_test", "{}");
            runOnUiThread(() -> {
                selfTestButton.setEnabled(true);
                try {
                    JSONObject result = new JSONObject(output);
                    if (result.optBoolean("ok", false)) {
                        statusView.setText("Epic runtime self-test passed");
                        noticeView.setText(result.optJSONObject("self_test").toString());
                    } else {
                        statusView.setText("Epic runtime self-test failed");
                        noticeView.setText(result.optString("message", output));
                    }
                } catch (Exception e) {
                    statusView.setText("Epic runtime self-test failed");
                    noticeView.setText(output);
                }
            });
        });
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        }
    }
}
