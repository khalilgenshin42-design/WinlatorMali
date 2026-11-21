package com.winlator.cmod;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.epic.EpicBridgeClient;
import com.winlator.cmod.epic.EpicLaunchPayloadStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EpicFragment extends Fragment {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView statusText;
    private Button cancelJobButton;
    private EditText authorizationCode;
    private Button installBridgeButton;
    private Button signInButton;
    private Button completeLoginButton;
    private Button libraryButton;
    private Button storageButton;
    private Spinner containerSpinner;
    private LinearLayout gamesContainer;
    private final List<Container> containers = new ArrayList<>();
    private final Set<String> installedApps = new HashSet<>();
    private final java.util.Map<String, String> installedPaths = new java.util.HashMap<>();
    private boolean replaceCompanion;
    private boolean storageReady;
    private boolean hostStorageReady;
    private boolean bridgeStorageReady;
    private volatile boolean jobPolling;
    private static final String JOB_PREFS = "epic_bridge_jobs";
    private static final String JOB_ID_KEY = "active_job_id";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(requireContext());
        title.setText("Epic Games Store");
        title.setTextSize(24);
        root.addView(title);

        statusText = new TextView(requireContext());
        statusText.setTextSize(14);
        statusText.setPadding(0, pad, 0, pad);
        root.addView(statusText);

        cancelJobButton = new Button(requireContext());
        cancelJobButton.setText("Cancel Epic operation");
        cancelJobButton.setVisibility(View.GONE);
        cancelJobButton.setOnClickListener(v -> cancelActiveJob());
        root.addView(cancelJobButton);

        installBridgeButton = new Button(requireContext());
        installBridgeButton.setText("Install Epic Bridge");
        installBridgeButton.setOnClickListener(v -> installBridge());
        root.addView(installBridgeButton);

        storageButton = new Button(requireContext());
        storageButton.setText("Open Epic Bridge storage settings");
        storageButton.setOnClickListener(v -> requestStorageAccess());
        root.addView(storageButton);

        signInButton = new Button(requireContext());
        signInButton.setText("Sign in with Epic Games");
        signInButton.setOnClickListener(v -> beginLogin());
        root.addView(signInButton);

        authorizationCode = new EditText(requireContext());
        authorizationCode.setSingleLine(true);
        authorizationCode.setHint("Paste Epic authorizationCode");
        root.addView(authorizationCode);

        TextView loginHint = new TextView(requireContext());
        loginHint.setText("Sign-in opens Epic's official login page. After signing in, copy the authorizationCode value from its response and paste it above.");
        loginHint.setTextSize(12);
        loginHint.setPadding(0, 0, 0, pad / 2);
        root.addView(loginHint);

        completeLoginButton = new Button(requireContext());
        completeLoginButton.setText("Complete Epic sign-in");
        completeLoginButton.setOnClickListener(v -> completeLogin());
        root.addView(completeLoginButton);

        containerSpinner = new Spinner(requireContext());
        root.addView(containerSpinner);
        loadContainers();

        libraryButton = new Button(requireContext());
        libraryButton.setText("Refresh Epic library");
        libraryButton.setOnClickListener(v -> refreshLibrary());
        root.addView(libraryButton);

        gamesContainer = new LinearLayout(requireContext());
        gamesContainer.setOrientation(LinearLayout.VERTICAL);
        gamesContainer.setPadding(0, pad, 0, 0);
        root.addView(gamesContainer);

        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (containerSpinner != null) loadContainers();
        resumePendingJob();
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

    private void loadContainers() {
        containers.clear();
        containers.addAll(new ContainerManager(requireContext()).getContainers());
        List<String> labels = new ArrayList<>();
        for (Container container : containers) {
            labels.add(container.getName() + " (ID " + container.id + ")");
        }
        if (labels.isEmpty()) labels.add("Create a Winlator container first");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        containerSpinner.setAdapter(adapter);
    }

    private void refreshStatus() {
        if (statusText == null || getContext() == null) return;
        boolean installed = EpicBridgeClient.isInstalled(requireContext());
        if (!installed) {
            statusText.setText("Epic Bridge is not installed. Install the companion package to continue.");
            installBridgeButton.setVisibility(View.VISIBLE);
            storageButton.setVisibility(View.GONE);
            setRuntimeControls(false);
            return;
        }

        Bundle bridge = EpicBridgeClient.getStatus(requireContext());
        boolean ready = bridge.getBoolean("runtime_ready", false);
        bridgeStorageReady = bridge.getBoolean("storage_access", false);
        hostStorageReady = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
        storageReady = bridgeStorageReady && hostStorageReady;
        storageButton.setText(!hostStorageReady
                ? "Grant MaliWinlator storage access"
                : "Open Epic Bridge storage settings");
        String state = bridge.getString("state", "");
        replaceCompanion = "signature_mismatch".equals(state) || "protocol_mismatch".equals(state) || "runtime_not_installed".equals(state);
        installBridgeButton.setVisibility(replaceCompanion ? View.VISIBLE : View.GONE);
        installBridgeButton.setText(replaceCompanion ? "Replace Epic Bridge" : "Install Epic Bridge");
        storageButton.setVisibility(storageReady || replaceCompanion ? View.GONE : View.VISIBLE);
        if (!ready) {
            statusText.setText("Epic Bridge is installed, but its CPython/Legendary runtime is not ready. " +
                    bridge.getString("message", "Install the runtime-enabled companion build."));
            setRuntimeControls(false);
            return;
        }

        statusText.setText(storageReady
                ? "Epic Bridge runtime ready. Checking Epic account..."
                : "Epic runtime ready. Grant shared-storage access in both apps before installing or launching games.");
        setRuntimeControls(true);
        runCommand("account_status", "{}", response -> {
            if (!response.optBoolean("ok", false)) {
                statusText.setText("Runtime ready. " + response.optString("message", "Epic account status unavailable"));
                return;
            }
            if (response.optBoolean("logged_in", false)) {
                JSONObject account = response.optJSONObject("account");
                String displayName = account == null ? "" : account.optString("display_name", "");
                statusText.setText("Signed in to Epic Games" +
                        (displayName.isEmpty() ? "" : " as " + displayName) + "." +
                        (storageReady ? "" : " Grant shared-storage access in both apps to install and launch games."));
            } else {
                statusText.setText("Epic runtime ready. Sign in to your Epic Games account to load your library." +
                        (storageReady ? "" : " Shared-storage access is also required for installs and launches."));
            }
        });
    }

    private void setRuntimeControls(boolean ready) {
        if (signInButton != null) signInButton.setEnabled(ready);
        if (completeLoginButton != null) completeLoginButton.setEnabled(ready);
        if (libraryButton != null) libraryButton.setEnabled(ready);
        if (authorizationCode != null) authorizationCode.setEnabled(ready);
        if (containerSpinner != null) containerSpinner.setEnabled(ready && !containers.isEmpty());
    }

    private interface CommandCallback {
        void onResult(JSONObject result);
    }

    private void runCommand(String method, String payload, CommandCallback callback) {
        android.content.Context appContext = requireContext().getApplicationContext();
        io.execute(() -> {
            JSONObject json;
            try {
                if ("install".equals(method) || "uninstall".equals(method)) {
                    android.content.SharedPreferences jobPrefs =
                            appContext.getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
                    String existingJobId = jobPrefs.getString(JOB_ID_KEY, "");
                    if (!existingJobId.isEmpty()) {
                        Bundle existingState = EpicBridgeClient.call(appContext, "job_status", existingJobId, "{}");
                        String existingStatus = existingState.getString("state", "unknown");
                        if (!"complete".equals(existingStatus) && !"failed".equals(existingStatus)
                                && !"interrupted".equals(existingStatus)) {
                            json = new JSONObject();
                            try {
                                json.put("ok", false);
                                json.put("message", "Another Epic operation is already running. Reopen the Epic tab to resume its status.");
                            } catch (Exception ignored) {}
                        } else {
                            jobPrefs.edit().remove(JOB_ID_KEY).apply();
                            Bundle started = EpicBridgeClient.call(appContext, "start_job", method, payload);
                            if (!started.getBoolean("ok", false)) {
                                json = new JSONObject();
                                try {
                                    json.put("ok", false);
                                    json.put("message", started.getString("message", "Unable to start Epic operation"));
                                } catch (Exception ignored) {}
                            } else {
                                String jobId = started.getString("job_id", "");
                                jobPrefs.edit().putString(JOB_ID_KEY, jobId).commit();
                                jobPolling = true;
                                setCancelButtonVisible("install".equals(method));
                                json = waitForJob(appContext, jobId);
                                jobPolling = false;
                                setCancelButtonVisible(false);
                            }
                        }
                    } else {
                        Bundle started = EpicBridgeClient.call(appContext, "start_job", method, payload);
                        if (!started.getBoolean("ok", false)) {
                            json = new JSONObject();
                            try {
                                json.put("ok", false);
                                json.put("message", started.getString("message", "Unable to start Epic operation"));
                            } catch (Exception ignored) {}
                        } else {
                            String jobId = started.getString("job_id", "");
                            jobPrefs.edit().putString(JOB_ID_KEY, jobId).apply();
                            jobPolling = true;
                            setCancelButtonVisible("install".equals(method));
                            json = waitForJob(appContext, jobId);
                            jobPolling = false;
                            setCancelButtonVisible(false);
                        }
                    }
                } else {
                    Bundle response = EpicBridgeClient.call(appContext, method, payload);
                    try {
                        String body = response.getString("result_json");
                        json = body == null ? new JSONObject() : new JSONObject(body);
                    } catch (Exception e) {
                        json = new JSONObject();
                        try {
                            json.put("ok", false);
                            json.put("message", response.getString("message", "Epic Bridge operation failed"));
                            json.put("error", response.getString("error", "IPC_ERROR"));
                        } catch (Exception ignored) {}
                    }
                }

            } catch (Exception error) {
                jobPolling = false;
                json = new JSONObject();
                try {
                    json.put("ok", false);
                    json.put("error", "IPC_ERROR");
                    json.put("message", error.getMessage() == null
                            ? "Epic Bridge request failed unexpectedly" : error.getMessage());
                } catch (Exception ignored) {}
            }

            JSONObject finalJson = json;
            if (getActivity() != null) getActivity().runOnUiThread(() -> {
                if (getView() != null && callback != null) callback.onResult(finalJson);
            });
        });
    }

    private JSONObject waitForJob(android.content.Context appContext, String jobId) {
        long deadline = System.currentTimeMillis() + 5L * 60L * 60L * 1000L;
        JSONObject result = new JSONObject();
        long unknownSince = 0L;
        while (!jobId.isEmpty() && System.currentTimeMillis() < deadline
                && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1500);
                Bundle state = EpicBridgeClient.call(appContext, "job_status", jobId, "{}");
                if (!state.getBoolean("ok", false)) {
                    result.put("ok", false);
                    result.put("message", state.getString("message",
                            "Epic Bridge is unavailable or no longer authorizes this host"));
                    return result;
                }
                String jobState = state.getString("state", "unknown");
                setCancelButtonVisible(state.getBoolean("can_cancel", false)
                        && ("running".equals(jobState) || "cancelling".equals(jobState)));
                String progressJson = state.getString("progress_json", "");
                if (!progressJson.isEmpty()) {
                    try {
                        JSONObject progress = new JSONObject(progressJson);
                        String phase = progress.optString("phase", "working");
                        double percent = progress.optDouble("progress", -1);
                        String label = "Epic " + phase;
                        if (percent >= 0) label += String.format(java.util.Locale.US, " (%.1f%%)", percent);
                        final String progressLabel = label;
                        if (getActivity() != null) getActivity().runOnUiThread(() -> {
                            if (statusText != null && jobPolling) statusText.setText(progressLabel);
                        });
                    } catch (Exception ignored) {}
                }
                if ("unknown".equals(jobState)) {
                    if (unknownSince == 0L) unknownSince = System.currentTimeMillis();
                    if (System.currentTimeMillis() - unknownSince > 30_000L) {
                        android.content.SharedPreferences prefs =
                                appContext.getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
                        if (jobId.equals(prefs.getString(JOB_ID_KEY, ""))) {
                            prefs.edit().remove(JOB_ID_KEY).apply();
                        }
                        result.put("ok", false);
                        result.put("message", "Epic Bridge did not report this job. The operation may not have started; retry it.");
                        return result;
                    }
                    continue;
                } else {
                    unknownSince = 0L;
                }
                if ("complete".equals(jobState) || "failed".equals(jobState)
                        || "cancelled".equals(jobState) || "interrupted".equals(jobState)) {
                    String body = state.getString("result_json", "");
                    result = body.isEmpty() ? new JSONObject() : new JSONObject(body);
                    if ("interrupted".equals(jobState)) {
                        try {
                            result.put("ok", false);
                            result.put("message", "Android stopped the Epic background service before the operation completed. Retry the install or update.");
                        } catch (Exception ignored) {}
                    }
                    android.content.SharedPreferences prefs =
                            appContext.getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
                    if (jobId.equals(prefs.getString(JOB_ID_KEY, ""))) {
                        prefs.edit().remove(JOB_ID_KEY).apply();
                    }
                    return result;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                try {
                    result.put("ok", false);
                    result.put("message", "Epic operation status polling failed: " + e.getMessage());
                } catch (Exception ignored) {}
                return result;
            }
        }
        try {
            result.put("ok", false);
            result.put("message", "Epic operation is still running in the foreground service. Reopen the Epic tab to resume status tracking.");
        } catch (Exception ignored) {}
        return result;
    }

    private void setCancelButtonVisible(boolean visible) {
        if (getActivity() != null) getActivity().runOnUiThread(() -> {
            if (cancelJobButton != null) cancelJobButton.setVisibility(
                    visible ? View.VISIBLE : View.GONE);
        });
    }

    private void cancelActiveJob() {
        if (getContext() == null) return;
        android.content.Context appContext = requireContext().getApplicationContext();
        // waitForJob occupies the single-thread IO executor while polling.
        // Use a separate short-lived thread so cancellation is never queued behind it.
        new Thread(() -> {
            android.content.SharedPreferences prefs = appContext.getSharedPreferences(
                    JOB_PREFS, android.content.Context.MODE_PRIVATE);
            String jobId = prefs.getString(JOB_ID_KEY, "");
            if (jobId.isEmpty()) return;
            Bundle response = EpicBridgeClient.call(appContext, "cancel_job", jobId, "{}");
            String message = response.getBoolean("ok", false)
                    ? "Cancellation requested. Waiting for download workers to stop safely..."
                    : response.getString("message", "Unable to cancel the Epic operation");
            if (getActivity() != null) getActivity().runOnUiThread(() -> {
                if (statusText != null) statusText.setText(message);
            });
        }, "EpicJobCancel").start();
    }

    private void resumePendingJob() {
        if (jobPolling || statusText == null || getContext() == null) return;
        android.content.Context appContext = requireContext().getApplicationContext();
        if (!EpicBridgeClient.isInstalled(appContext)) return;
        android.content.SharedPreferences prefs =
                appContext.getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
        String jobId = prefs.getString(JOB_ID_KEY, "");
        if (jobId.isEmpty()) return;

        jobPolling = true;
        setCancelButtonVisible(false);
        statusText.setText("Resuming the previous Epic operation...");
        io.execute(() -> {
            JSONObject result = waitForJob(appContext, jobId);
            jobPolling = false;
            setCancelButtonVisible(false);
            if (getActivity() != null) getActivity().runOnUiThread(() -> {
                if (getView() == null || statusText == null) return;
                if (result.optBoolean("ok", false)) {
                    statusText.setText("The previous Epic operation completed.");
                    refreshLibrary();
                } else {
                    statusText.setText(result.optString("message", "Epic operation is still running."));
                }
            });
        });
    }

    private void beginLogin() {
        statusText.setText("Opening Epic Games sign-in...");
        runCommand("begin_login", "{}", result -> {
            if (!result.optBoolean("ok", false)) {
                statusText.setText("Unable to start Epic sign-in: " + result.optString("message"));
                return;
            }
            String url = result.optString("authorization_url", "");
            if (url.isEmpty()) {
                statusText.setText("Epic did not return a sign-in URL.");
                return;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                statusText.setText("Sign in through Epic's official page, then paste the authorizationCode response above.");
            } catch (Exception e) {
                statusText.setText("Unable to open the Epic sign-in page: " + e.getMessage());
            }
        });
    }

    private void completeLogin() {
        String code = authorizationCode.getText().toString().trim();
        if (code.startsWith("{")) {
            try { code = new JSONObject(code).optString("authorizationCode", code); }
            catch (Exception ignored) {}
        }
        if (code.isEmpty()) {
            authorizationCode.setError("Enter the Epic authorizationCode");
            return;
        }
        JSONObject payload = new JSONObject();
        try { payload.put("authorization_code", code); }
        catch (Exception ignored) {}
        statusText.setText("Completing Epic sign-in...");
        runCommand("finish_login", payload.toString(), result -> {
            if (!result.optBoolean("ok", false)) {
                statusText.setText("Epic sign-in failed: " + result.optString("message"));
                return;
            }
            authorizationCode.setText("");
            JSONObject account = result.optJSONObject("account");
            statusText.setText("Signed in to Epic Games" +
                    (account == null ? "" : " as " + account.optString("display_name", "")) + ".");
            refreshLibrary();
        });
    }

    private void refreshLibrary() {
        if (gamesContainer == null) return;
        gamesContainer.removeAllViews();
        TextView loading = new TextView(requireContext());
        loading.setText("Loading Epic library...");
        gamesContainer.addView(loading);
        runCommand("library", "{}", result -> {
            gamesContainer.removeAllViews();
            if (!result.optBoolean("ok", false)) {
                statusText.setText("Unable to load Epic library: " + result.optString("message"));
                return;
            }
            JSONArray games = result.optJSONArray("games");
            if (games == null || games.length() == 0) {
                TextView empty = new TextView(requireContext());
                empty.setText("No installable Windows games were returned by Epic.");
                gamesContainer.addView(empty);
                return;
            }
            loadInstalledThenRender(games);
        });
    }

    private void loadInstalledThenRender(JSONArray games) {
        runCommand("installed", "{}", installed -> {
            installedApps.clear();
            installedPaths.clear();
            JSONArray current = installed.optJSONArray("games");
            if (current != null) {
                for (int i = 0; i < current.length(); i++) {
                    JSONObject game = current.optJSONObject(i);
                    if (game != null) {
                        String appName = game.optString("app_name");
                        installedApps.add(appName);
                        installedPaths.put(appName, game.optString("install_path", ""));
                    }
                }
            }
            for (int i = 0; i < games.length(); i++) {
                JSONObject game = games.optJSONObject(i);
                if (game != null && !game.optBoolean("is_dlc", false)) addGameRow(game);
            }
        });
    }

    private void addGameRow(JSONObject game) {
        String appName = game.optString("app_name");
        String title = game.optString("title", appName);
        boolean installed = installedApps.contains(appName);
        boolean supported = game.optString("version", "").length() > 0
                && game.optString("third_party_store", "").isEmpty();

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 12, 0, 16);
        TextView name = new TextView(requireContext());
        name.setText(title + (installed ? "  •  Installed" : ""));
        name.setTextSize(16);
        row.addView(name);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        if (!installed && supported && storageReady) {
            Button install = new Button(requireContext());
            install.setText("Install");
            install.setOnClickListener(v -> installGame(appName, title));
            actions.addView(install);
        }
        if (installed && storageReady) {
            Button launch = new Button(requireContext());
            launch.setText("Launch");
            launch.setOnClickListener(v -> launchGame(appName, title));
            actions.addView(launch);

            Button update = new Button(requireContext());
            update.setText("Update");
            update.setOnClickListener(v -> installGame(appName, title, true));
            actions.addView(update);

            Button uninstall = new Button(requireContext());
            uninstall.setText("Uninstall");
            uninstall.setOnClickListener(v -> uninstallGame(appName, title));
            actions.addView(uninstall);
        }
        if (!supported && !installed) {
            TextView unsupported = new TextView(requireContext());
            unsupported.setText("Not directly installable with Legendary (third-party or no Windows build).");
            actions.addView(unsupported);
        }
        if (installed && !storageReady) {
            TextView permission = new TextView(requireContext());
            permission.setText("Grant shared-storage access to launch or uninstall this game.");
            actions.addView(permission);
        }
        row.addView(actions);
        View divider = new View(requireContext());
        divider.setBackgroundColor(0x55333333);
        row.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        gamesContainer.addView(row);
    }

    private void installGame(String appName, String title) {
        installGame(appName, title, false);
    }

    private void installGame(String appName, String title, boolean update) {
        if (!storageReady) {
            statusText.setText("Grant shared-storage access to both MaliWinlator and Epic Bridge before installing games.");
            return;
        }
        if (containers.isEmpty()) {
            statusText.setText("Create a Winlator container before installing Epic games.");
            return;
        }
        if (update) {
            int ownerIndex = findContainerIndex(installedPaths.get(appName));
            if (ownerIndex < 0) {
                statusText.setText("This game's install path is not inside a managed Wine container. Reinstall it into a selected container before updating.");
                return;
            }
            containerSpinner.setSelection(ownerIndex);
        }
        int selected = containerSpinner.getSelectedItemPosition();
        if (selected < 0 || selected >= containers.size()) {
            statusText.setText("Select a valid Winlator container before installing.");
            return;
        }
        Container targetContainer = containers.get(selected);
        File basePath = getEpicContainerDirectory(targetContainer);
        try {
            ensureEpicDriveLink(targetContainer);
        } catch (Exception e) {
            statusText.setText("Unable to connect Epic storage to container " +
                    targetContainer.getName() + ": " + e.getMessage());
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("app_name", appName);
            payload.put("base_path", basePath.getAbsolutePath());
            payload.put("max_workers", 4);
            payload.put("max_shared_memory_mib", 512);
        } catch (Exception ignored) {}
        statusText.setText((update ? "Updating " : "Installing ") + title +
                ". The foreground download service keeps working if you leave this tab.");
        runCommand("install", payload.toString(), result -> {
            if (!result.optBoolean("ok", false)) {
                statusText.setText((update ? "Update failed: " : "Install failed: ") +
                        result.optString("message"));
                return;
            }
            String installMessage = title + (update ? " updated in " : " installed in ") +
                    targetContainer.getName() + ". Select that container to launch the game.";
            JSONObject prerequisites = result.optJSONObject("prerequisites");
            if (prerequisites != null && !prerequisites.optString("path", "").isEmpty()) {
                installMessage += " Epic lists a prerequisite installer (" +
                        prerequisites.optString("name", "required component") +
                        "); it is not launched automatically by this build.";
            }
            JSONArray warnings = result.optJSONArray("warnings");
            if (warnings != null && warnings.length() > 0) {
                installMessage += " Compatibility warnings: " + warnings.toString();
            }
            statusText.setText(installMessage);
            refreshLibrary();
        });
    }

    private File getEpicContainerDirectory(Container container) {
        return new File(SettingsFragment.DEFAULT_WINLATOR_PATH,
                "EpicGames/container-" + container.id);
    }

    private void ensureEpicDriveLink(Container container) throws java.io.IOException {
        File target = getEpicContainerDirectory(container);
        if (!target.exists() && !target.mkdirs()) {
            throw new java.io.IOException("Unable to create shared Epic game directory: " + target);
        }
        if (!target.isDirectory()) {
            throw new java.io.IOException("Shared Epic game path is not a directory: " + target);
        }

        File games = new File(container.getRootDir(), ".wine/drive_c/Games");
        if (!games.exists() && !games.mkdirs()) {
            throw new java.io.IOException("Unable to create Wine C:/Games directory: " + games);
        }
        File link = new File(games, "Epic");
        java.nio.file.Path linkPath = link.toPath();
        java.nio.file.Path targetPath = target.toPath();

        if (java.nio.file.Files.isSymbolicLink(linkPath)) {
            java.nio.file.Path existing = java.nio.file.Files.readSymbolicLink(linkPath);
            File resolved = existing.isAbsolute()
                    ? existing.toFile() : new File(games, existing.toString());
            if (!resolved.getCanonicalPath().equals(target.getCanonicalPath())) {
                throw new java.io.IOException("Wine C:/Games/Epic already points to a different directory");
            }
            return;
        }

        if (link.exists()) {
            if (!link.isDirectory()) {
                throw new java.io.IOException("Wine C:/Games/Epic is occupied by a non-directory path");
            }
            File[] children = link.listFiles();
            if (children != null && children.length > 0) {
                throw new java.io.IOException("Wine C:/Games/Epic already contains files; refusing to replace it");
            }
            if (!link.delete()) {
                throw new java.io.IOException("Unable to replace empty Wine C:/Games/Epic directory");
            }
        }
        java.nio.file.Files.createSymbolicLink(linkPath, targetPath);
    }

    private int findContainerIndex(String installPath) {
        if (installPath == null || installPath.isEmpty()) return -1;
        try {
            String candidate = new File(installPath).getCanonicalPath();
            for (int i = 0; i < containers.size(); i++) {
                String root = getEpicContainerDirectory(containers.get(i)).getCanonicalPath();
                if (candidate.startsWith(root + File.separator)) return i;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private String getWineEpicDirectory(Container container, String gameDirectory)
            throws java.io.IOException {
        String root = getEpicContainerDirectory(container).getCanonicalPath();
        String actual = new File(gameDirectory).getCanonicalPath();
        if (!actual.startsWith(root + File.separator)) {
            throw new java.io.IOException("Epic game directory is outside its container-specific shared folder");
        }
        String relative = actual.substring(root.length() + 1)
                .replace(File.separatorChar, '\\');
        return "C:\\Games\\Epic\\" + relative;
    }

    private void uninstallGame(String appName, String title) {
        JSONObject payload = new JSONObject();
        try { payload.put("app_name", appName); }
        catch (Exception ignored) {}
        statusText.setText("Uninstalling " + title + "...");
        runCommand("uninstall", payload.toString(), result -> {
            statusText.setText(result.optBoolean("ok", false)
                    ? title + " uninstalled." : "Uninstall failed: " + result.optString("message"));
            refreshLibrary();
        });
    }

    private void launchGame(String appName, String title) {
        if (containers.isEmpty()) {
            statusText.setText("Create a Winlator container before launching Epic games.");
            return;
        }
        int ownerIndex = findContainerIndex(installedPaths.get(appName));
        if (ownerIndex < 0) {
            statusText.setText("This game's install path is not inside a managed Wine container. Reinstall it into a selected container before launching.");
            return;
        }
        containerSpinner.setSelection(ownerIndex);
        Container container = containers.get(ownerIndex);
        try {
            ensureEpicDriveLink(container);
        } catch (Exception e) {
            statusText.setText("Unable to restore this container's Epic C: mapping: " + e.getMessage());
            return;
        }

        JSONObject payload = new JSONObject();
        try { payload.put("app_name", appName); }
        catch (Exception ignored) {}
        statusText.setText("Preparing " + title + " for Wine...");
        runCommand("launch_info", payload.toString(), result -> {
            if (!result.optBoolean("ok", false)) {
                statusText.setText("Unable to prepare launch: " + result.optString("message"));
                return;
            }
            JSONObject launch = result.optJSONObject("launch");
            if (launch == null) {
                statusText.setText("Epic returned incomplete launch information.");
                return;
            }
            String exe = launch.optString("game_executable", "");
            String gameDir = launch.optString("game_directory", "");
            if (exe.isEmpty() || gameDir.isEmpty()) {
                statusText.setText("Epic game executable path is missing.");
                return;
            }
            if (findContainerIndex(gameDir) != ownerIndex) {
                statusText.setText("Epic returned a game directory outside the selected container's shared game folder. Reinstall the game into this container.");
                return;
            }
            String epicWorkingDirectory = launch.optString("working_directory", gameDir);
            if (findContainerIndex(epicWorkingDirectory) != ownerIndex) {
                statusText.setText("Epic returned a working directory outside the selected container's shared game folder.");
                return;
            }
            File executable = new File(gameDir, exe.replace('\\', File.separatorChar));
            if (!executable.isFile()) {
                statusText.setText("Game executable not found: " + executable);
                return;
            }
            try {
                String gameRoot = new File(gameDir).getCanonicalPath();
                String executablePath = executable.getCanonicalPath();
                if (!executablePath.startsWith(gameRoot + File.separator)) {
                    statusText.setText("Epic returned an executable outside the selected game's install directory.");
                    return;
                }
            } catch (Exception e) {
                statusText.setText("Unable to validate the Epic executable path: " + e.getMessage());
                return;
            }

            String ownershipTokenPath;
            try {
                ownershipTokenPath = materializeOwnershipToken(launch, container);
            } catch (Exception e) {
                statusText.setText("Unable to prepare Epic ownership token for Wine: " + e.getMessage());
                return;
            }
            List<String> args = new ArrayList<>();
            appendArguments(args, launch.optJSONArray("game_parameters"), null);
            appendArguments(args, launch.optJSONArray("egl_parameters"), ownershipTokenPath);
            appendArguments(args, launch.optJSONArray("user_parameters"), null);
            String commandLine = joinArguments(args);
            EnvVars environment = new EnvVars();
            JSONObject env = launch.optJSONObject("environment");
            if (env != null) {
                java.util.Iterator<String> keys = env.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    environment.put(key, env.optString(key, ""));
                }
            }
            final String wineWorkingDirectory;
            try {
                wineWorkingDirectory = getWineEpicDirectory(container, epicWorkingDirectory);
            } catch (Exception e) {
                statusText.setText("Unable to map Epic game directory into Wine C:: " + e.getMessage());
                return;
            }
            String token = EpicLaunchPayloadStore.put(commandLine, wineWorkingDirectory, environment);
            Intent intent = new Intent(requireContext(), XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            intent.putExtra("shortcut_path", executable.getAbsolutePath());
            intent.putExtra("shortcut_name", title);
            intent.putExtra("epic_launch_token", token);
            startActivity(intent);
        });
    }

    private String materializeOwnershipToken(JSONObject launch, Container container) throws Exception {
        JSONArray secretFiles = launch.optJSONArray("secret_files");
        if (secretFiles == null || secretFiles.length() == 0) return null;

        File secretDir = new File(container.getRootDir(), "tmp/epic-secrets");
        if (!secretDir.exists() && !secretDir.mkdirs()) {
            throw new java.io.IOException("Unable to create container-private Epic token directory");
        }
        File[] stale = secretDir.listFiles();
        if (stale != null) {
            long cutoff = System.currentTimeMillis() - 30L * 60L * 1000L;
            for (File file : stale) if (file.lastModified() < cutoff) file.delete();
        }

        String winePath = null;
        for (int i = 0; i < secretFiles.length(); i++) {
            JSONObject secret = secretFiles.optJSONObject(i);
            if (secret == null) continue;
            String name = secret.optString("name", "");
            if (!name.matches("epic-[A-Za-z0-9_-]+\\.ovt")) {
                throw new java.io.IOException("Invalid Epic ownership-token filename");
            }
            byte[] data = Base64.decode(secret.optString("data_base64", ""), Base64.DEFAULT);
            if (data.length == 0 || data.length > 256 * 1024) {
                throw new java.io.IOException("Epic ownership token has an invalid size");
            }
            File target = new File(secretDir, name);
            try (FileOutputStream output = new FileOutputStream(target, false)) {
                output.write(data);
                output.getFD().sync();
            }
            target.setReadable(false, false);
            target.setReadable(true, true);
            String imageRoot = ImageFs.find(requireContext()).getRootDir().getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            if (!targetPath.startsWith(imageRoot + File.separator)) {
                throw new java.io.IOException("Epic ownership token escaped the container image root");
            }
            String relativePath = targetPath.substring(imageRoot.length() + 1)
                    .replace(File.separatorChar, '\\');
            winePath = "Z:\\" + relativePath;
            File cleanupTarget = target;
            new Handler(Looper.getMainLooper()).postDelayed(cleanupTarget::delete, 30L * 60L * 1000L);
        }
        return winePath;
    }

    private static void appendArguments(List<String> output, JSONArray input, String ownershipTokenPath) {
        if (input == null) return;
        for (int i = 0; i < input.length(); i++) {
            String value = input.optString(i, "");
            if (value.equals("-epicovt=__EPIC_OVT_PLACEHOLDER__")) {
                if (ownershipTokenPath == null) continue;
                value = "-epicovt=" + ownershipTokenPath;
            }
            if (!value.isEmpty()) output.add(value);
        }
    }

    private static String joinArguments(List<String> args) {
        StringBuilder result = new StringBuilder();
        for (String arg : args) {
            if (result.length() > 0) result.append(' ');
            result.append(quoteWindowsArgument(arg));
        }
        return result.toString();
    }

    private static String quoteWindowsArgument(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        int backslashes = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\') {
                backslashes++;
            } else if (ch == '"') {
                for (int j = 0; j < backslashes * 2 + 1; j++) quoted.append('\\');
                quoted.append('"');
                backslashes = 0;
            } else {
                for (int j = 0; j < backslashes; j++) quoted.append('\\');
                quoted.append(ch);
                backslashes = 0;
            }
        }
        for (int j = 0; j < backslashes * 2; j++) quoted.append('\\');
        quoted.append('"');
        return quoted.toString();
    }

    private void installBridge() {
        try {
            if (replaceCompanion) {
                EpicBridgeClient.requestUninstall(requireContext());
                return;
            }
            EpicBridgeClient.requestInstall(requireContext());
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Unable to open Epic Bridge installer: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestStorageAccess() {
        try {
            if (!hostStorageReady && Build.VERSION.SDK_INT >= 30) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
            } else {
                openBridge();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Unable to open storage settings: " + e.getMessage(),
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
