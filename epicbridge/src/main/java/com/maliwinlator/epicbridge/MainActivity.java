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

        TextView notice = new TextView(this);
        notice.setPadding(0, pad, 0, 0);
        notice.setText("This companion keeps the Epic account runtime separate from MaliWinlator. The Epic runtime is not included in this bootstrap build yet.");
        root.addView(notice);

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
        statusView.setText(hasStorageAccess()
                ? "Shared game-library access: granted\nRuntime: not installed"
                : "Shared game-library access: required\nRuntime: not installed");
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
