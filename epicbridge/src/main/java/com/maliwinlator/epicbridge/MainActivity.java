package com.maliwinlator.epicbridge;

import android.app.Activity;
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

public class MainActivity extends Activity {
    private TextView statusView;
    private TextView noticeView;

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

        setContentView(root);
        refreshStatus();
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
        statusView.setText((hasStorageAccess()
                ? "Shared game-library access: granted"
                : "Shared game-library access: required")
                + "\nCPython / Legendary runtime: "
                + (runtimePackaged ? "bundled" : "not bundled"));
        noticeView.setText(runtimePackaged
                ? "Epic account data and Legendary run in this separate companion package. Return to MaliWinlator to sign in and manage your Epic library."
                : "This is a bootstrap companion build. Epic login, library, downloads and launch features become available when the Android CPython/Legendary runtime is bundled.");
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
