package com.winlator.cmod;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.cmod.epic.EpicBridgeClient;

public final class EpicFragment extends Fragment {
    private TextView statusText;
    private Button installButton;
    private Button storageButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(requireContext());
        title.setText("Epic Games Store");
        title.setTextSize(24);
        root.addView(title);

        statusText = new TextView(requireContext());
        statusText.setTextSize(15);
        statusText.setPadding(0, pad, 0, pad);
        root.addView(statusText);

        installButton = new Button(requireContext());
        installButton.setText("Install Epic Bridge");
        installButton.setOnClickListener(v -> installBridge());
        root.addView(installButton);

        storageButton = new Button(requireContext());
        storageButton.setText("Open Epic Bridge storage settings");
        storageButton.setOnClickListener(v -> openBridge());
        root.addView(storageButton);

        Button refreshButton = new Button(requireContext());
        refreshButton.setText("Refresh status");
        refreshButton.setOnClickListener(v -> refreshStatus());
        root.addView(refreshButton);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ThemeManager.applyThemeToView(view, getContext());
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle("Epic Games");
            }
        }
    }

    private void refreshStatus() {
        if (statusText == null || getContext() == null) return;
        boolean installed = EpicBridgeClient.isInstalled(requireContext());
        if (!installed) {
            statusText.setText("Epic Bridge is not installed. Install the companion package to continue. The Epic runtime is still being prepared.");
            installButton.setVisibility(View.VISIBLE);
            storageButton.setVisibility(View.GONE);
            return;
        }

        Bundle status = EpicBridgeClient.getStatus(requireContext());
        boolean ready = status.getBoolean("runtime_ready", false);
        boolean storage = status.getBoolean("storage_access", false);
        String message = status.getString("message", "Epic Bridge is installed");
        statusText.setText(ready
                ? "Epic Bridge is ready. " + message
                : "Epic Bridge installed. " + message + (storage ? "" : "\n\nGrant shared-storage access in the companion app before installing games."));
        installButton.setVisibility(View.GONE);
        storageButton.setVisibility(storage ? View.GONE : View.VISIBLE);
    }

    private void installBridge() {
        try {
            EpicBridgeClient.requestInstall(requireContext());
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Unable to open Epic Bridge installer: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openBridge() {
        try {
            Intent launch = requireContext().getPackageManager()
                    .getLaunchIntentForPackage(EpicBridgeClient.PACKAGE_NAME);
            if (launch != null) startActivity(launch);
            else Toast.makeText(requireContext(), "Epic Bridge activity not found", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Unable to open Epic Bridge", Toast.LENGTH_LONG).show();
        }
    }
}
