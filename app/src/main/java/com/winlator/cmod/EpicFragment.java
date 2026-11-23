package com.winlator.cmod;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

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
import java.util.concurrent.atomic.AtomicInteger;

public final class EpicFragment extends Fragment {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    // Keep provider readiness/asset probes off the UI thread and independent of
    // the long-lived install worker, which may be polling a multi-minute job.
    private final ExecutorService statusIo = Executors.newSingleThreadExecutor();
    private final AtomicInteger statusRequestSerial = new AtomicInteger();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
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
    private LinearLayout pageContainer;
    private LinearLayout navigationBar;
    private LinearLayout accountPanel;
    private LinearLayout libraryPanel;
    private EditText searchField;
    private TextView libraryCountText;
    private TextView downloadsStatusText;
    private EpicLibraryViewModel libraryState;
    private JSONArray cachedGames = new JSONArray();
    private boolean libraryLoaded;
    private String activeSection = "discover";
    private volatile String lastProgressLabel = "No active Epic download or installation.";
    private final List<Container> containers = new ArrayList<>();
    private final Set<String> installedApps = new HashSet<>();
    private final java.util.Map<String, String> installedPaths = new java.util.HashMap<>();
    private boolean replaceCompanion;
    private boolean storageReady;
    private boolean hostStorageReady;
    private boolean bridgeStorageReady;
    private boolean embeddedRuntime;
    private volatile boolean jobPolling;
    private static final String JOB_PREFS = "epic_bridge_jobs";
    private static final String JOB_ID_KEY = "active_job_id";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        // MainActivity replaces EpicFragment whenever the user changes drawer sections.
        // Keep library data in the Activity-scoped ViewModel so a fresh Fragment can
        // render immediately without repeating the Epic library IPC request.
        libraryState = new ViewModelProvider(requireActivity()).get(EpicLibraryViewModel.class);
        cachedGames = libraryState.getGames();
        libraryLoaded = libraryState.isLoaded();
        installedApps.addAll(libraryState.getInstalledApps());
        installedPaths.putAll(libraryState.getInstalledPaths());

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff0a0c12);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(28));
        scroll.addView(root);

        LinearLayout brand = new LinearLayout(requireContext());
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = label("EPIC GAMES", 20, 0xfff5f6fa, true);
        brand.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edition = label("MALIWINLATOR", 10, 0xffaeb8cb, true);
        edition.setPadding(dp(10), dp(6), dp(10), dp(6));
        edition.setBackground(shape(0xff20283a, 0xff20283a, 30));
        brand.addView(edition);
        root.addView(brand);

        statusText = label("Connecting to Epic Bridge...", 12, 0xffb8c2d5, false);
        statusText.setPadding(dp(12), dp(11), dp(12), dp(11));
        statusText.setBackground(shape(0xff171d29, 0xff293247, 12));
        addMargin(root, statusText, 14);

        LinearLayout nav = new LinearLayout(requireContext());
        navigationBar = nav;
        nav.setPadding(dp(3), dp(3), dp(3), dp(3));
        nav.setBackground(shape(0xff151a25, 0xff293247, 14));
        String[][] tabs = {{"discover","Discover"},{"library","Library"},{"downloads","Downloads"},{"account","Account"}};
        for (String[] tab : tabs) {
            Button b = action(tab[1], false);
            b.setTextSize(11);
            b.setMinHeight(dp(40));
            b.setPadding(dp(2), 0, dp(2), 0);
            b.setTag(tab[0]);
            b.setOnClickListener(v -> showSection(tab[0]));
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(42), 1));
        }
        addMargin(root, nav, 14);

        pageContainer = new LinearLayout(requireContext());
        pageContainer.setOrientation(LinearLayout.VERTICAL);
        addMargin(root, pageContainer, 18);

        accountPanel = new LinearLayout(requireContext());
        accountPanel.setOrientation(LinearLayout.VERTICAL);
        addHeading(accountPanel, "Epic account", "Sign in and configure where your Windows games are installed.");

        installBridgeButton = action("Install Epic Bridge", true);
        installBridgeButton.setOnClickListener(v -> installBridge());
        accountPanel.addView(installBridgeButton, fullParams());

        storageButton = action("Storage permissions", false);
        storageButton.setOnClickListener(v -> requestStorageAccess());
        addMargin(accountPanel, storageButton, 8);

        signInButton = action("Sign in with Epic Games", true);
        signInButton.setOnClickListener(v -> beginLogin());
        addMargin(accountPanel, signInButton, 18);

        authorizationCode = new EditText(requireContext());
        authorizationCode.setSingleLine(true);
        authorizationCode.setHint("Paste Epic authorizationCode");
        authorizationCode.setTextColor(0xfff5f6fa);
        authorizationCode.setHintTextColor(0xff77839a);
        authorizationCode.setPadding(dp(13), 0, dp(13), 0);
        authorizationCode.setBackground(shape(0xff171d29, 0xff303b50, 11));
        addMargin(accountPanel, authorizationCode, 10);

        TextView hint = label("Sign in through Epic's official page, then paste the authorizationCode value from its response.", 11, 0xff929db1, false);
        accountPanel.addView(hint);

        completeLoginButton = action("Complete Epic sign-in", false);
        completeLoginButton.setOnClickListener(v -> completeLogin());
        addMargin(accountPanel, completeLoginButton, 8);

        addHeading(accountPanel, "Install destination", "Choose the Winlator container for new Epic games.");
        containerSpinner = new Spinner(requireContext());
        accountPanel.addView(containerSpinner, fullParams());
        loadContainers();

        libraryPanel = new LinearLayout(requireContext());
        libraryPanel.setOrientation(LinearLayout.VERTICAL);
        addHeading(libraryPanel, "Your library", "Your Windows-compatible Epic titles.");
        searchField = new EditText(requireContext());
        searchField.setSingleLine(true);
        searchField.setHint("Search your games");
        searchField.setTextColor(0xfff5f6fa);
        searchField.setHintTextColor(0xff77839a);
        searchField.setPadding(dp(13), 0, dp(13), 0);
        searchField.setBackground(shape(0xff171d29, 0xff303b50, 11));
        libraryPanel.addView(searchField, fullParams());

        LinearLayout tools = new LinearLayout(requireContext());
        tools.setGravity(Gravity.CENTER_VERTICAL);
        libraryCountText = label("Sign in to load your games.", 11, 0xff929db1, false);
        tools.addView(libraryCountText, new LinearLayout.LayoutParams(0, -2, 1));
        libraryButton = action("Refresh", false);
        libraryButton.setOnClickListener(v -> refreshLibrary());
        tools.addView(libraryButton);
        addMargin(libraryPanel, tools, 10);

        gamesContainer = new LinearLayout(requireContext());
        gamesContainer.setOrientation(LinearLayout.VERTICAL);
        addMargin(libraryPanel, gamesContainer, 10);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderGameCards(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        // The new Fragment may be replacing an older one that already loaded the
        // library. Populate its detached card container now; showSection() will then
        // attach a complete, ready-to-use Library without a network/provider reload.
        if (libraryLoaded) renderGameCards("");

        downloadsStatusText = label(lastProgressLabel, 14, 0xfff1f4fb, true);
        cancelButtonVisibility.set(-1);
        cancelJobButton = action("Cancel Epic operation", false);
        cancelJobButton.setOnClickListener(v -> cancelActiveJob());
        cancelJobButton.setVisibility(View.GONE);

        showSection(activeSection);
        return scroll;
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != fill) d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView t = new TextView(requireContext());
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button action(String value, boolean primary) {
        Button b = new Button(requireContext());
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setMinHeight(dp(44));
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setTextColor(0xfff5f6fa);
        b.setBackground(shape(primary ? 0xff5b55d9 : 0xff252f43,
                primary ? 0xff5b55d9 : 0xff35425a, 11));
        return b;
    }

    private LinearLayout.LayoutParams fullParams() {
        return new LinearLayout.LayoutParams(-1, dp(46));
    }

    private void addMargin(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        parent.addView(child, p);
    }

    private void addHeading(LinearLayout parent, String title, String detail) {
        parent.addView(label(title, 21, 0xfff5f6fa, true));
        TextView subtitle = label(detail, 12, 0xff929db1, false);
        subtitle.setPadding(0, dp(5), 0, dp(14));
        parent.addView(subtitle);
    }

    private void showSection(String section) {
        activeSection = section;
        if (navigationBar != null) {
            for (int i = 0; i < navigationBar.getChildCount(); i++) {
                View child = navigationBar.getChildAt(i);
                if (child instanceof Button) {
                    Button tab = (Button) child;
                    boolean selected = section.equals(tab.getTag());
                    tab.setBackground(shape(selected ? 0xff5b55d9 : 0xff151a25,
                            selected ? 0xff5b55d9 : 0xff151a25, 10));
                    tab.setTextColor(selected ? 0xffffffff : 0xffaab3c5);
                }
            }
        }
        if (pageContainer == null) return;
        pageContainer.removeAllViews();
        if ("library".equals(section)) {
            pageContainer.addView(libraryPanel);
            if (!libraryLoaded) refreshLibrary();
        } else if ("account".equals(section)) {
            pageContainer.addView(accountPanel);
        } else if ("downloads".equals(section)) {
            addHeading(pageContainer, "Downloads", "Your Epic install continues in the foreground.");
            LinearLayout panel = new LinearLayout(requireContext());
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(16), dp(16), dp(16), dp(16));
            panel.setBackground(shape(0xff171d29, 0xff303b50, 15));
            panel.addView(label("CURRENT ACTIVITY", 10, 0xffaaa6ff, true));
            downloadsStatusText.setText(lastProgressLabel);
            // pageContainer.removeAllViews() detaches the old panel, not its
            // children. Reparent the reusable status view before adding it to
            // this newly-created Downloads panel, otherwise Android throws
            // "The specified child already has a parent" on every re-entry.
            if (downloadsStatusText.getParent() instanceof ViewGroup) {
                ((ViewGroup) downloadsStatusText.getParent()).removeView(downloadsStatusText);
            }
            addMargin(panel, downloadsStatusText, 10);
            TextView note = label("You can leave this page while the foreground service downloads or installs. Progress and cancellation remain available here.", 11, 0xff929db1, false);
            addMargin(panel, note, 8);
            pageContainer.addView(panel);
            if (cancelJobButton.getParent() instanceof ViewGroup) {
                ((ViewGroup)cancelJobButton.getParent()).removeView(cancelJobButton);
            }
            addMargin(pageContainer, cancelJobButton, 12);
            android.content.SharedPreferences prefs = requireContext().getApplicationContext()
                    .getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
            String jobId = prefs.getString(JOB_ID_KEY, "");
            if (!jobId.isEmpty() && !jobPolling) {
                downloadsStatusText.setText("Saved Epic operation found. Reconnecting to its foreground status...");
                // Entering Downloads is an explicit recovery point after a provider
                // interruption. Attach a fresh observer without starting another job.
                resumePendingJob();
            } else if (jobId.isEmpty() && !jobPolling) {
                downloadsStatusText.setText("No active download or installation.");
            }
        } else {
            LinearLayout hero = new LinearLayout(requireContext());
            hero.setOrientation(LinearLayout.VERTICAL);
            hero.setPadding(dp(20), dp(22), dp(20), dp(20));
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xff3c347f, 0xff202b48, 0xff111827});
            bg.setCornerRadius(dp(21));
            hero.setBackground(bg);
            hero.addView(label("YOUR PC GAME COLLECTION", 10, 0xffd0ceff, true));
            TextView title = label("Your Epic library.\nReady for Winlator.", 27, 0xffffffff, true);
            title.setPadding(0, dp(12), 0, dp(8));
            hero.addView(title);
            hero.addView(label("Browse your Windows games, install them into a Wine container, and keep track of downloads.", 12, 0xffd2d7e8, false));
            Button browse = action("Explore my library", true);
            browse.setOnClickListener(v -> showSection("library"));
            addMargin(hero, browse, 18);
            pageContainer.addView(hero);
            addHeading(pageContainer, "Quick access", "Everything for your Epic games, in one place.");
            addQuickLink(pageContainer, "Game library", "Browse owned games and manage installs.", "library");
            addQuickLink(pageContainer, "Downloads", "Check current download or installation status.", "downloads");
            addQuickLink(pageContainer, "Account & setup", "Sign in, grant storage access, and select a container.", "account");
        }
    }

    private void addQuickLink(LinearLayout parent, String title, String detail, String target) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(14), dp(13), dp(14), dp(13));
        item.setBackground(shape(0xff171d29, 0xff293247, 13));
        item.addView(label(title, 14, 0xfff5f6fa, true));
        TextView subtitle = label(detail, 11, 0xff929db1, false);
        subtitle.setPadding(0, dp(4), 0, 0);
        item.addView(subtitle);
        item.setOnClickListener(v -> showSection(target));
        addMargin(parent, item, 9);
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
        if (containerSpinner == null) return;
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
        final android.content.Context appContext = requireContext().getApplicationContext();
        final int requestSerial = statusRequestSerial.incrementAndGet();
        statusText.setText("Checking Epic Bridge runtime...");
        statusIo.execute(() -> {
            boolean installed = EpicBridgeClient.isInstalled(appContext);
            Bundle bridge = installed ? EpicBridgeClient.getStatus(appContext) : null;
            boolean localStorageReady = Build.VERSION.SDK_INT < 30
                    || Environment.isExternalStorageManager();

            mainHandler.post(() -> {
                // Ignore stale results and callbacks delivered after the view was removed.
                if (requestSerial != statusRequestSerial.get()
                        || !isAdded() || getView() == null || statusText == null) return;
                if (!installed) {
                    statusText.setText("Epic Bridge is not installed. Install the companion package to continue.");
                    installBridgeButton.setVisibility(View.VISIBLE);
                    storageButton.setVisibility(View.GONE);
                    setRuntimeControls(false);
                    return;
                }

                boolean ready = bridge != null && bridge.getBoolean("runtime_ready", false);
                embeddedRuntime = bridge != null && bridge.getBoolean("host_embedded", false);
                bridgeStorageReady = bridge != null && bridge.getBoolean("storage_access", false);
                hostStorageReady = localStorageReady;
                storageReady = bridgeStorageReady && hostStorageReady;
                storageButton.setText(!hostStorageReady
                        ? "Grant MaliWinlator storage access"
                        : "Open Epic Bridge storage settings");
                String state = bridge == null ? "provider_unavailable" : bridge.getString("state", "");
                replaceCompanion = "signature_mismatch".equals(state)
                        || "protocol_mismatch".equals(state)
                        || "runtime_not_installed".equals(state);
                installBridgeButton.setVisibility(replaceCompanion ? View.VISIBLE : View.GONE);
                installBridgeButton.setText(replaceCompanion ? "Replace Epic Bridge" : "Install Epic Bridge");
                storageButton.setVisibility(storageReady || replaceCompanion ? View.GONE : View.VISIBLE);
                if (!ready) {
                    String message = bridge == null ? "Epic Bridge provider is unavailable."
                            : bridge.getString("message", "Install the runtime-enabled companion build.");
                    statusText.setText("Epic Bridge is installed, but its CPython/Legendary runtime is not ready. " + message);
                    setRuntimeControls(false);
                    return;
                }

                // The embedded CPython runtime serializes calls while Legendary is
                // downloading/installing. Do not enqueue account_status on every
                // return to this page while a persisted foreground job is active.
                // The job monitor owns the status line until it reaches a terminal state.
                android.content.SharedPreferences jobPrefs = appContext.getSharedPreferences(
                        JOB_PREFS, android.content.Context.MODE_PRIVATE);
                String activeJobId = jobPrefs.getString(JOB_ID_KEY, "");
                setRuntimeControls(true);
                if (!activeJobId.isEmpty()) {
                    statusText.setText(storageReady
                            ? "Epic installation is continuing in the foreground. Progress is shown below."
                            : "Epic installation is continuing. Grant shared-storage access before starting another operation.");
                    return;
                }

                statusText.setText(storageReady
                        ? "Epic Bridge runtime ready. Checking Epic account..."
                        : (embeddedRuntime
                                ? "Epic runtime ready. Grant shared-storage access to MaliWinlator before installing or launching games."
                                : "Epic runtime ready. Grant shared-storage access in both apps before installing or launching games."));
                runCommand("account_status", "{}", response -> {
                    if (statusText == null || getView() == null) return;
                    if (!response.optBoolean("ok", false)) {
                        statusText.setText("Runtime ready. " + response.optString("message", "Epic account status unavailable"));
                        return;
                    }
                    if (response.optBoolean("logged_in", false)) {
                        JSONObject account = response.optJSONObject("account");
                        String displayName = account == null ? "" : account.optString("display_name", "");
                        statusText.setText("Signed in to Epic Games" +
                                (displayName.isEmpty() ? "" : " as " + displayName) + "." +
                                (storageReady ? "" : (embeddedRuntime
                                        ? " Grant shared-storage access to MaliWinlator to install and launch games."
                                        : " Grant shared-storage access in both apps to install and launch games.")));
                    } else {
                        statusText.setText("Epic runtime ready. Sign in to your Epic Games account to load your library." +
                                (storageReady ? "" : " Shared-storage access is also required for installs and launches."));
                    }
                });
            });
        });
    }

    @Override
    public void onDestroy() {
        statusRequestSerial.incrementAndGet();
        // The foreground service owns the actual download/install operation.
        // Stop this Fragment's observers when the Fragment is permanently
        // destroyed so a replacement Epic screen cannot leave duplicate pollers.
        // waitForJob() treats interruption as a detach and leaves the persisted
        // job ID intact for the next Fragment instance to resume monitoring.
        io.shutdownNow();
        statusIo.shutdownNow();
        super.onDestroy();
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
        String lastPostedProgressLabel = null;
        while (!jobId.isEmpty() && System.currentTimeMillis() < deadline
                && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1500);
                Bundle state = EpicBridgeClient.call(appContext, "job_status", jobId, "{}");
                if (!state.getBoolean("ok", false)) {
                    String message = state.getString("message",
                            "Epic Bridge is unavailable or no longer authorizes this host");
                    result.put("ok", false);
                    result.put("message", message);
                    publishJobStatusLabel("Epic status unavailable: " + message);
                    return result;
                }
                String jobState = state.getString("state", "unknown");
                setCancelButtonVisible(state.getBoolean("can_cancel", false)
                        && ("running".equals(jobState) || "cancelling".equals(jobState)));
                String progressJson = state.getString("progress_json", "");
                {
                    try {
                        // Always render a live state even before the first
                        // progress snapshot is published by the Python worker.
                        // This avoids leaving Downloads stuck on "Reconnecting"
                        // when the foreground notification already confirms work.
                        String fallbackLabel = "running".equals(jobState)
                                ? "Epic download/install in progress..."
                                : "cancelling".equals(jobState)
                                        ? "Cancelling Epic operation..."
                                        : "queued".equals(jobState)
                                                ? "Epic operation queued..."
                                                : "Epic operation: " + jobState;
                        String label = fallbackLabel;
                        if (!progressJson.isEmpty()) {
                            JSONObject progress = new JSONObject(progressJson);
                            String phase = progress.optString("phase", "working");
                            double percent = progress.optDouble("progress", -1);
                            label = "Epic " + phase;
                            if (percent >= 0) label += String.format(java.util.Locale.US, " (%.1f%%)", percent);
                        }
                        final String progressLabel = label;
                        // Job status is polled every 1.5 seconds, but progress can
                        // remain unchanged for many polls. Avoid flooding the main
                        // queue with identical TextView updates during slow phases.
                        if (!progressLabel.equals(lastPostedProgressLabel)) {
                            lastPostedProgressLabel = progressLabel;
                            lastProgressLabel = progressLabel;
                            mainHandler.post(() -> {
                                if (statusText != null && jobPolling) {
                                    statusText.setText(progressLabel);
                                }
                                if (downloadsStatusText != null) {
                                    downloadsStatusText.setText(progressLabel);
                                }
                            });
                        }
                    } catch (Exception ignored) {}
                }
                if ("unknown".equals(jobState)) {
                    if (unknownSince == 0L) unknownSince = System.currentTimeMillis();
                    if (System.currentTimeMillis() - unknownSince > 30_000L) {
                        // "unknown" is not proof that the foreground operation stopped.
                        // Preserve its only recovery handle; the notification/service may
                        // still be active. Downloads can attach a new observer on re-entry.
                        result.put("ok", false);
                        result.put("pending", true);
                        result.put("message", "Epic Bridge temporarily cannot report this operation. Its saved job ID was retained. Open Downloads again to reconnect; do not start a second install yet.");
                        publishJobStatusLabel("Epic status temporarily unavailable — saved operation retained.");
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
                    lastProgressLabel = "Epic operation " + jobState + ".";
                    mainHandler.post(() -> {
                        if (downloadsStatusText != null) downloadsStatusText.setText(lastProgressLabel);
                    });
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
                String message = "Epic operation status polling failed: " + e.getMessage();
                try {
                    result.put("ok", false);
                    result.put("message", message);
                } catch (Exception ignored) {}
                publishJobStatusLabel(message);
                return result;
            }
        }
        try {
            result.put("ok", false);
            result.put("message", "Epic operation is still running in the foreground service. Reopen the Epic tab to resume status tracking.");
        } catch (Exception ignored) {}
        return result;
    }

    private void publishJobStatusLabel(String label) {
        lastProgressLabel = label;
        mainHandler.post(() -> {
            if (downloadsStatusText != null) downloadsStatusText.setText(label);
            if (statusText != null && jobPolling) statusText.setText(label);
        });
    }

    private final AtomicInteger cancelButtonVisibility = new AtomicInteger(-1);

    private void setCancelButtonVisible(boolean visible) {
        final int visibility = visible ? View.VISIBLE : View.GONE;
        if (cancelButtonVisibility.getAndSet(visibility) == visibility) return;
        mainHandler.post(() -> {
            if (cancelJobButton != null
                    && cancelJobButton.getVisibility() != visibility) {
                cancelJobButton.setVisibility(visibility);
            }
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
        // Do not synchronously call the provider from onResume/UI thread.
        // The first job_status request below validates the provider asynchronously.
        android.content.SharedPreferences prefs =
                appContext.getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
        String jobId = prefs.getString(JOB_ID_KEY, "");
        if (jobId.isEmpty()) return;

        jobPolling = true;
        setCancelButtonVisible(false);
        publishJobStatusLabel("Reconnecting to the saved Epic operation...");
        statusText.setText("Reconnecting to the saved Epic operation...");
        io.execute(() -> {
            JSONObject result = waitForJob(appContext, jobId);
            jobPolling = false;
            setCancelButtonVisible(false);
            if (getActivity() != null) getActivity().runOnUiThread(() -> {
                if (getView() == null || statusText == null) return;
                if (result.optBoolean("ok", false)) {
                    publishJobStatusLabel("The previous Epic operation completed.");
                    statusText.setText("The previous Epic operation completed.");
                    refreshLibrary();
                } else {
                    String message = result.optString("message", "Epic operation is still running.");
                    publishJobStatusLabel(message);
                    statusText.setText(message);
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
        gamesContainer.addView(label("Loading your Epic library...", 13, 0xffaab3c5, false));
        runCommand("library", "{}", result -> {
            if (!result.optBoolean("ok", false)) {
                gamesContainer.removeAllViews();
                statusText.setText("Unable to load Epic library: " + result.optString("message"));
                libraryCountText.setText("Library could not be loaded.");
                return;
            }
            JSONArray games = result.optJSONArray("games");
            if (games == null) games = new JSONArray();
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
            cachedGames = games;
            libraryLoaded = true;
            if (libraryState != null) {
                libraryState.setLibrary(games, installedApps, installedPaths);
            }
            renderGameCards(searchField == null ? "" : searchField.getText().toString());
        });
    }

    private void renderGameCards(String query) {
        if (gamesContainer == null) return;
        gamesContainer.removeAllViews();
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        LinearLayout row = null;
        int inRow = 2;
        int count = 0;
        for (int i = 0; i < cachedGames.length(); i++) {
            JSONObject game = cachedGames.optJSONObject(i);
            if (game == null || game.optBoolean("is_dlc", false)) continue;
            String title = game.optString("title", game.optString("app_name"));
            if (!needle.isEmpty() && !title.toLowerCase(java.util.Locale.ROOT).contains(needle)) continue;
            if (inRow == 2) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                gamesContainer.addView(row);
                inRow = 0;
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.rightMargin = dp(4);
            params.leftMargin = inRow == 1 ? dp(4) : 0;
            params.bottomMargin = dp(9);
            row.addView(createGameCard(game), params);
            inRow++;
            count++;
        }
        if (count == 0) {
            gamesContainer.addView(label(needle.isEmpty() ? "No Windows games found in this Epic library." : "No games match your search.", 12, 0xff929db1, false));
        }
        libraryCountText.setText(count + (count == 1 ? " game" : " games"));
    }

    private View createGameCard(JSONObject game) {
        String appName = game.optString("app_name");
        String title = game.optString("title", appName);
        boolean installed = installedApps.contains(appName);
        boolean supported = game.optString("version", "").length() > 0
                && game.optString("third_party_store", "").isEmpty();

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9), dp(9), dp(9), dp(10));
        card.setBackground(shape(0xff171d29, 0xff293247, 14));

        FrameLayout artwork = new FrameLayout(requireContext());
        GradientDrawable art = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{artColor(appName, 0), artColor(appName, 1), 0xff111827});
        art.setCornerRadius(dp(9));
        ImageView cover = new ImageView(requireContext());
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(art);
        artwork.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        String coverUrl = game.optString("cover_url", "");
        if (isTrustedEpicArtworkUrl(coverUrl)) {
            Glide.with(this)
                    .load(coverUrl)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(art)
                    .error(art)
                    .centerCrop()
                    .into(cover);
        } else {
            TextView initials = label(gameMonogram(title), 28, 0xfff5f6fa, true);
            initials.setGravity(Gravity.CENTER);
            artwork.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        }
        TextView tag = label(installed ? "INSTALLED" : "EPIC LIBRARY", 8,
                installed ? 0xffb9f3d1 : 0xffd3d0ff, true);
        tag.setPadding(dp(6), dp(4), dp(6), dp(4));
        tag.setBackground(shape(0x8830394f, 0x8830394f, 5));
        FrameLayout.LayoutParams tagParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
        tagParams.setMargins(dp(6), 0, 0, dp(6));
        artwork.addView(tag, tagParams);
        card.addView(artwork, new LinearLayout.LayoutParams(-1, dp(102)));

        TextView name = label(title, 12, 0xfff5f6fa, true);
        name.setMaxLines(2);
        name.setMinLines(2);
        name.setPadding(dp(1), dp(8), dp(1), dp(3));
        card.addView(name);
        card.addView(label(installed ? "Ready to play" :
                (supported ? "Not installed" : "Not directly installable"), 10,
                installed ? 0xff79d6a2 : 0xff929db1, false));

        if (installed && storageReady) {
            Button play = action("Play", true);
            play.setOnClickListener(v -> launchGame(appName, title));
            addMargin(card, play, 7);
            Button manage = action("Update / Remove", false);
            manage.setOnClickListener(v -> showInstalledActions(appName, title));
            addMargin(card, manage, 5);
        } else if (!installed && supported && storageReady) {
            Button install = action("Install", true);
            install.setOnClickListener(v -> installGame(appName, title));
            addMargin(card, install, 7);
        } else if (!storageReady) {
            TextView permission = label("Storage access required", 10, 0xffffc978, false);
            addMargin(card, permission, 7);
        } else {
            TextView unsupported = label("Requires another store or has no Windows build.", 10, 0xff929db1, false);
            addMargin(card, unsupported, 7);
        }
        return card;
    }

    private void showInstalledActions(String appName, String title) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setItems(new String[]{"Update", "Uninstall", "Cancel"}, (dialog, which) -> {
                    if (which == 0) installGame(appName, title, true);
                    else if (which == 1) uninstallGame(appName, title);
                }).show();
    }

    private boolean isTrustedEpicArtworkUrl(String value) {
        if (value == null || value.length() > 2048) return false;
        try {
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(scheme) || host == null) return false;
            host = host.toLowerCase(java.util.Locale.ROOT);
            return host.equals("epicgames.com") || host.endsWith(".epicgames.com")
                    || host.equals("unrealengine.com") || host.endsWith(".unrealengine.com");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private int artColor(String seed, int offset) {
        int hash = Math.abs((seed == null ? 0 : seed.hashCode()) + offset * 7919);
        int[] palette = {0xff5146a8,0xff185b70,0xff80512f,0xff69436e,
                0xff315d45,0xff465c91,0xff78503f,0xff4b557b};
        return palette[hash % palette.length];
    }

    private String gameMonogram(String title) {
        if (title == null || title.trim().isEmpty()) return "EP";
        String[] words = title.trim().split("\\s+");
        if (words.length == 1) return words[0].substring(0, Math.min(2, words[0].length())).toUpperCase(java.util.Locale.ROOT);
        return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(java.util.Locale.ROOT);
    }

    private void installGame(String appName, String title) {
        installGame(appName, title, false);
    }

    private void installGame(String appName, String title, boolean update) {
        if (!storageReady) {
            statusText.setText(embeddedRuntime
                    ? "Grant MaliWinlator shared-storage access before installing games."
                    : "Grant shared-storage access to both MaliWinlator and Epic Bridge before installing games.");
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
            payload.put("max_workers", 2);
            payload.put("max_shared_memory_mib", 256);
        } catch (Exception ignored) {}
        statusText.setText((update ? "Updating " : "Installing ") + title +
                ". The foreground download service keeps working if you leave this tab.");
        runCommand("install", payload.toString(), result -> {
            if (!result.optBoolean("ok", false)) {
                if (result.optBoolean("pending", false)) {
                    statusText.setText(result.optString("message"));
                    return;
                }
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
            if (result.optBoolean("pending", false)) {
                statusText.setText(result.optString("message"));
                return;
            }
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
