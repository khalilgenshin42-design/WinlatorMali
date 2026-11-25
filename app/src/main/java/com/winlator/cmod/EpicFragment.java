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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ProgressBar;
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
    // Long-lived job polling must never occupy the command executor: game-details,
    // library and account requests still need to run while a download is active.
    private final ExecutorService jobIo = Executors.newSingleThreadExecutor();
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
    private LinearLayout featuredContainer;
    private LinearLayout pageContainer;
    private ScrollView storefrontScroll;
    private ScrollView accountScroll;
    private ScrollView profileScroll;
    private int storefrontScrollY;
    private LinearLayout navigationBar;
    private View brandHeader;
    private LinearLayout accountPanel;
    private LinearLayout libraryPanel;
    private EditText searchField;
    private TextView libraryCountText;
    private TextView downloadsStatusText;
    private EpicLibraryViewModel libraryState;
    private JSONArray cachedGames = new JSONArray();
    private boolean libraryLoaded;
    private String activeSection = "library";
    private JSONObject selectedGame;
    private String selectedGameAppName = "";
    private TextView gameProgressText;
    private ProgressBar gameProgressBar;
    private Button gamePrimaryButton;
    private boolean gameJobActive;
    private boolean showingGameDetails;
    // One-shot entrance motion: filtering and progress-driven re-renders must not
    // replay the entire storefront animation on every keystroke or refresh.
    private boolean hasRenderedGameCards;
    private boolean animateGameCardEntrance;
    private boolean hasAnimatedFeaturedHero;
    private String libraryFilter = "All games";
    private final java.util.Map<String, Button> libraryFilterButtons = new java.util.LinkedHashMap<>();
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
    private static final String ACTIVE_JOB_APP_KEY = "active_job_app_name";

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

        // Fixed header plus independent scroll viewports for each destination.
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(18));
        root.setBackgroundColor(0xff0a0c12);

        LinearLayout brand = new LinearLayout(requireContext());
        brandHeader = brand;
        brand.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brandCopy = new LinearLayout(requireContext());
        brandCopy.setOrientation(LinearLayout.VERTICAL);
        TextView logo = label("EPIC GAMES", 22, 0xfff5f6fa, true);
        brandCopy.addView(logo);
        TextView brandSubtitle = label("YOUR PC GAME COLLECTION", 9, 0xff9ca8bf, true);
        brandSubtitle.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams brandSubtitleParams = new LinearLayout.LayoutParams(-2, -2);
        brandSubtitleParams.topMargin = dp(2);
        brandCopy.addView(brandSubtitle, brandSubtitleParams);
        brand.addView(brandCopy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edition = label("MALIWINLATOR", 9, 0xffd9d5ff, true);
        edition.setPadding(dp(10), dp(6), dp(10), dp(6));
        edition.setBackground(shape(0xff20283a, 0xff20283a, 30));
        brand.addView(edition);
        Button accountShortcut = action("ACCOUNT  ↗", false);
        accountShortcut.setTextSize(9);
        accountShortcut.setMinHeight(dp(36));
        accountShortcut.setPadding(dp(9), 0, dp(9), 0);
        accountShortcut.setOnClickListener(v -> showSection("account"));
        LinearLayout.LayoutParams accountShortcutParams =
                new LinearLayout.LayoutParams(-2, dp(36));
        accountShortcutParams.leftMargin = dp(8);
        brand.addView(accountShortcut, accountShortcutParams);
        root.addView(brand);

        // Runtime diagnostics belong to Account, not the player-facing storefront.
        statusText = label("Connecting to Epic Bridge...", 12, 0xffb8c2d5, false);
        statusText.setPadding(dp(13), dp(12), dp(13), dp(12));
        statusText.setBackground(shape(0xff171d29, 0xff293247, 12));

        // Keep Account reachable from the compact header action. The storefront
        // itself now owns the full page rather than sharing space with a settings tab.
        navigationBar = null;

        pageContainer = new LinearLayout(requireContext());
        pageContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        pageParams.topMargin = dp(18);
        root.addView(pageContainer, pageParams);

        accountPanel = new LinearLayout(requireContext());
        accountPanel.setOrientation(LinearLayout.VERTICAL);
        addHeading(accountPanel, "Account & setup", "Manage your Epic connection and game installation destination.");
        addMargin(accountPanel, statusText, 2);

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
        accountScroll = new ScrollView(requireContext());
        accountScroll.setFillViewport(true);
        accountScroll.setClipToPadding(false);
        accountScroll.addView(accountPanel);

        libraryPanel = new LinearLayout(requireContext());
        libraryPanel.setOrientation(LinearLayout.VERTICAL);
        libraryPanel.setPadding(0, dp(3), 0, 0);
        // Lead with the selected game's cinematic artwork. Collection controls
        // follow the hero so the home screen reads like a game launcher, not a
        // settings page with a featured tile appended beneath a large heading.
        featuredContainer = new LinearLayout(requireContext());
        featuredContainer.setOrientation(LinearLayout.VERTICAL);
        addMargin(libraryPanel, featuredContainer, 8);
        libraryPanel.addView(label("YOUR COLLECTION  /  EPIC GAMES", 9, 0xffaaa6ff, true));
        TextView libraryIntro = label("Your games.", 27, 0xfff5f6fa, true);
        libraryIntro.setPadding(0, dp(3), 0, dp(1));
        libraryPanel.addView(libraryIntro);
        TextView librarySubtitle = label("Pick up where you left off, or find your next game.", 11, 0xff929db1, false);
        librarySubtitle.setPadding(0, 0, 0, dp(12));
        libraryPanel.addView(librarySubtitle);
        searchField = new EditText(requireContext());
        searchField.setSingleLine(true);
        searchField.setHint("Search your games");
        searchField.setTextColor(0xfff5f6fa);
        searchField.setHintTextColor(0xff77839a);
        searchField.setPadding(dp(13), 0, dp(13), 0);
        searchField.setBackground(shape(0xff171d29, 0xff303b50, 11));
        libraryPanel.addView(searchField, new LinearLayout.LayoutParams(-1, dp(42)));

        HorizontalScrollView filterScroll = new HorizontalScrollView(requireContext());
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.setClipToPadding(false);
        LinearLayout filters = new LinearLayout(requireContext());
        filters.setOrientation(LinearLayout.HORIZONTAL);
        String[] filterNames = {"All games", "Installed", "Ready to install", "Downloading"};
        for (String filterName : filterNames) {
            Button filterButton = action(filterName, filterName.equals(libraryFilter));
            filterButton.setTextSize(11);
            filterButton.setMinHeight(dp(34));
            filterButton.setPadding(dp(13), 0, dp(13), 0);
            filterButton.setOnClickListener(v -> {
                libraryFilter = filterName;
                for (int i = 0; i < filters.getChildCount(); i++) {
                    View child = filters.getChildAt(i);
                    if (child instanceof Button) {
                        boolean selected = filterName.equals(child.getTag());
                        child.setBackground(shape(selected ? 0xff5b55d9 : 0xff252f43,
                                selected ? 0xff5b55d9 : 0xff35425a, 11));
                    }
                }
                renderGameCards(searchField.getText().toString());
            });
            filterButton.setTag(filterName);
            libraryFilterButtons.put(filterName, filterButton);
            filterButton.setContentDescription(filterName + " filter");
            LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-2, dp(36));
            filterParams.rightMargin = dp(8);
            filters.addView(filterButton, filterParams);
        }
        filterScroll.addView(filters);
        addMargin(libraryPanel, filterScroll, 12);

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
        storefrontScroll = new ScrollView(requireContext());
        storefrontScroll.setFillViewport(true);
        storefrontScroll.setClipToPadding(false);
        storefrontScroll.addView(libraryPanel);
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
        return root;
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
        if (brandHeader != null) brandHeader.setVisibility(View.VISIBLE);
        if (navigationBar != null) navigationBar.setVisibility(View.VISIBLE);
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
        // A destination can be detached while another screen is shown; record its
        // live offset before detaching so Account/Library round-trips stay stable.
        if (storefrontScroll != null && storefrontScroll.getParent() == pageContainer) {
            storefrontScrollY = storefrontScroll.getScrollY();
        }
        pageContainer.removeAllViews();
        if ("account".equals(section)) {
            showingGameDetails = false;
            if (accountScroll.getParent() != null) ((ViewGroup) accountScroll.getParent()).removeView(accountScroll);
            pageContainer.addView(accountScroll, new LinearLayout.LayoutParams(-1, -1));
        } else {
            showingGameDetails = false;
            if (storefrontScroll.getParent() != null) ((ViewGroup) storefrontScroll.getParent()).removeView(storefrontScroll);
            pageContainer.addView(storefrontScroll, new LinearLayout.LayoutParams(-1, -1));
            if (storefrontScroll != null) {
                storefrontScroll.post(() -> storefrontScroll.scrollTo(0, Math.max(0, storefrontScrollY)));
            }
            if (!libraryLoaded) refreshLibrary();
        }
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
        jobIo.shutdownNow();
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
            String monitorJobId = "";
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
                            json.put("ok", false);
                            json.put("message", "Another Epic operation is already running. Reopen the Epic tab to resume its status.");
                        } else {
                            jobPrefs.edit().remove(JOB_ID_KEY).remove(ACTIVE_JOB_APP_KEY).apply();
                            Bundle started = EpicBridgeClient.call(appContext, "start_job", method, payload);
                            if (!started.getBoolean("ok", false)) {
                                json = jobStartFailure(started);
                            } else {
                                monitorJobId = started.getString("job_id", "");
                                if (monitorJobId.isEmpty()) {
                                    json = invalidJobIdResult();
                                } else {
                                    jobPrefs.edit().putString(JOB_ID_KEY, monitorJobId).commit();
                                    if ("install".equals(method)) {
                                        jobPrefs.edit().putString(ACTIVE_JOB_APP_KEY,
                                                new JSONObject(payload).optString("app_name", "")).apply();
                                    }
                                    json = null;
                                }
                            }
                        }
                    } else {
                        Bundle started = EpicBridgeClient.call(appContext, "start_job", method, payload);
                        if (!started.getBoolean("ok", false)) {
                            json = jobStartFailure(started);
                        } else {
                            monitorJobId = started.getString("job_id", "");
                            if (monitorJobId.isEmpty()) {
                                json = invalidJobIdResult();
                            } else {
                                jobPrefs.edit().putString(JOB_ID_KEY, monitorJobId).apply();
                                if ("install".equals(method)) {
                                    jobPrefs.edit().putString(ACTIVE_JOB_APP_KEY,
                                            new JSONObject(payload).optString("app_name", "")).apply();
                                }
                                json = null;
                            }
                        }
                    }
                } else {
                    Bundle response = EpicBridgeClient.call(appContext, method, payload);
                    try {
                        String body = response.getString("result_json");
                        json = body == null ? new JSONObject() : new JSONObject(body);
                    } catch (Exception e) {
                        json = new JSONObject();
                        json.put("ok", false);
                        json.put("message", response.getString("message", "Epic Bridge operation failed"));
                        json.put("error", response.getString("error", "IPC_ERROR"));
                    }
                }
            } catch (Exception error) {
                json = new JSONObject();
                try {
                    json.put("ok", false);
                    json.put("error", "IPC_ERROR");
                    json.put("message", error.getMessage() == null
                            ? "Epic Bridge request failed unexpectedly" : error.getMessage());
                } catch (Exception ignored) {}
            }

            if (!monitorJobId.isEmpty()) {
                final String jobId = monitorJobId;
                jobPolling = true;
                setCancelButtonVisible("install".equals(method));
                submitJobMonitor(() -> {
                    JSONObject result = waitForJob(appContext, jobId);
                    jobPolling = false;
                    setCancelButtonVisible(false);
                    deliverCommandResult(callback, result);
                });
                return;
            }
            deliverCommandResult(callback, json);
        });
    }

    private boolean submitJobMonitor(Runnable monitor) {
        try {
            jobIo.execute(monitor);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException detached) {
            // The Fragment may have been destroyed after start_job returned but
            // before its observer was queued. The foreground service still owns
            // the operation and JOB_ID_KEY was persisted; a replacement Fragment
            // will reconnect in onResume(). Do not let this lifecycle race crash
            // the host process or clear the recovery handle.
            jobPolling = false;
            return false;
        }
    }

    private JSONObject jobStartFailure(Bundle started) {
        JSONObject json = new JSONObject();
        try {
            json.put("ok", false);
            json.put("message", started.getString("message", "Unable to start Epic operation"));
            json.put("error", started.getString("error", "EPIC_JOB_START_FAILED"));
        } catch (Exception ignored) {}
        return json;
    }

    private JSONObject invalidJobIdResult() {
        JSONObject json = new JSONObject();
        try {
            json.put("ok", false);
            json.put("error", "INVALID_JOB_ID");
            json.put("message", "Epic Bridge returned an empty job ID");
        } catch (Exception ignored) {}
        return json;
    }

    private void deliverCommandResult(CommandCallback callback, JSONObject result) {
        if (getActivity() != null) getActivity().runOnUiThread(() -> {
            if (getView() != null && callback != null) callback.onResult(result);
        });
    }

    private JSONObject waitForJob(android.content.Context appContext, String jobId) {
        android.content.SharedPreferences jobPrefs = appContext
                .getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
        final String monitoredAppName = jobPrefs.getString(ACTIVE_JOB_APP_KEY, "");
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
                        double percent = -1;
                        if (!progressJson.isEmpty()) {
                            JSONObject progress = new JSONObject(progressJson);
                            String phase = progress.optString("phase", "working");
                            percent = progress.optDouble("progress", -1);
                            label = "Epic " + phase;
                            if (percent >= 0) label += String.format(java.util.Locale.US, " (%.1f%%)", percent);
                        }
                        final String progressLabel = label;
                        final double displayPercent = percent;
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
                                if (downloadsStatusText != null) downloadsStatusText.setText(progressLabel);
                                // A single Epic job may run while the user browses other
                                // game profiles. Keep its progress and cancel action scoped
                                // to the title that actually owns the persisted job.
                                if (monitoredAppName.equals(selectedGameAppName)) {
                                    if (gameProgressText != null) gameProgressText.setText(progressLabel);
                                    if (gameProgressBar != null) {
                                        if (displayPercent >= 0) {
                                            gameProgressBar.setIndeterminate(false);
                                            gameProgressBar.setProgress((int) Math.max(0, Math.min(100,
                                                    Math.round(displayPercent))));
                                        } else {
                                            gameProgressBar.setIndeterminate(true);
                                        }
                                    }
                                    if ("running".equals(jobState) || "queued".equals(jobState)
                                            || "cancelling".equals(jobState)) {
                                        gameJobActive = true;
                                        updateGameActionButton("cancelling".equals(jobState)
                                                ? "Cancelling..." : "Cancel download", true);
                                    } else if ("complete".equals(jobState) || "failed".equals(jobState)
                                            || "cancelled".equals(jobState) || "interrupted".equals(jobState)) {
                                        gameJobActive = false;
                                        JSONObject currentGame = findCachedGame(selectedGameAppName);
                                        String nextAction = installedApps.contains(selectedGameAppName)
                                                ? "Play game" : (isGameInstallSupported(currentGame)
                                                ? "Install" : "Unavailable");
                                        updateGameActionButton(nextAction, installedApps.contains(selectedGameAppName)
                                                || (isGameInstallSupported(currentGame) && storageReady));
                                    }
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
                        prefs.edit().remove(JOB_ID_KEY).remove(ACTIVE_JOB_APP_KEY).apply();
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
        android.content.Context context = getContext();
        String activeAppName = "";
        if (context != null) {
            android.content.SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
            activeAppName = prefs.getString(ACTIVE_JOB_APP_KEY, "");
        }
        final String jobAppName = activeAppName;
        mainHandler.post(() -> {
            if (downloadsStatusText != null) downloadsStatusText.setText(label);
            if (!jobAppName.isEmpty() && jobAppName.equals(selectedGameAppName)
                    && gameProgressText != null) {
                gameProgressText.setText(label);
            }
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
                if (gameProgressText != null) gameProgressText.setText(message);
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
        gameJobActive = true;
        selectedGameAppName = prefs.getString(ACTIVE_JOB_APP_KEY, "");
        selectedGame = findCachedGame(selectedGameAppName);
        if (selectedGame != null && "library".equals(activeSection)) {
            showGameDetails(selectedGame);
        }

        jobPolling = true;
        setCancelButtonVisible(false);
        publishJobStatusLabel("Reconnecting to the saved Epic operation...");
        statusText.setText("Reconnecting to the saved Epic operation...");
        submitJobMonitor(() -> {
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
            if (showingGameDetails && selectedGame != null) {
                showGameDetails(findCachedGame(selectedGameAppName));
            } else if (selectedGame == null) {
                android.content.SharedPreferences jobPrefs = requireContext().getApplicationContext()
                        .getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
                String activeJobId = jobPrefs.getString(JOB_ID_KEY, "");
                String activeAppName = jobPrefs.getString(ACTIVE_JOB_APP_KEY, "");
                if (!activeJobId.isEmpty() && !activeAppName.isEmpty()) {
                    selectedGameAppName = activeAppName;
                    selectedGame = findCachedGame(activeAppName);
                    gameJobActive = true;
                    if (selectedGame != null) showGameDetails(selectedGame);
                }
            }
        });
    }

    private void renderGameCards(String query) {
        if (gamesContainer == null || !isAdded()) return;
        gamesContainer.removeAllViews();
        updateLibraryFilterCounts();
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        animateGameCardEntrance = !hasRenderedGameCards && needle.isEmpty()
                && "All games".equals(libraryFilter);
        if (needle.isEmpty() && "All games".equals(libraryFilter)) {
            renderFeaturedHero();
            renderLibraryStats();
        }

        int count = 0;
        if ("Downloading".equals(libraryFilter)) {
            count += addGameShelf("IN PROGRESS", "downloading", needle);
        } else if ("Installed".equals(libraryFilter)) {
            count += addGameShelf("INSTALLED ON THIS DEVICE", "installed", needle);
        } else if ("Ready to install".equals(libraryFilter)) {
            count += addGameShelf("READY FOR INSTALLATION", "ready", needle);
        } else {
            count += addGameShelf("CONTINUE PLAYING", "recent", needle);
            count += addGameShelf("INSTALLED ON THIS DEVICE", "installed", needle);
            count += addGameShelf("ACTIVE DOWNLOADS & INSTALLS", "downloading", needle);
            count += addGameShelf("READY TO INSTALL", "ready", needle);
            count += addGameShelf("YOUR EPIC COLLECTION", "all", needle);
        }
        if (count == 0) {
            String emptyMessage;
            if (!needle.isEmpty()) emptyMessage = "No games match your search.";
            else if ("Installed".equals(libraryFilter)) emptyMessage = "No installed games yet.";
            else if ("Ready to install".equals(libraryFilter)) emptyMessage = "No games are currently ready to install.";
            else if ("Downloading".equals(libraryFilter)) emptyMessage = "No active Epic downloads or installations.";
            else emptyMessage = "No Windows games found in this Epic library.";
            LinearLayout empty = new LinearLayout(requireContext());
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(dp(18), dp(20), dp(18), dp(20));
            empty.setBackground(shape(0xff151b28, 0xff303b50, 16));
            empty.addView(label("NOTHING HERE YET", 9, 0xffaaa6ff, true));
            addMargin(empty, label(emptyMessage, 13, 0xffdce3f2, true), 7);
            gamesContainer.addView(empty);
        }
        libraryCountText.setText(count + (count == 1 ? " GAME" : " GAMES"));
        if (count > 0) hasRenderedGameCards = true;
        animateGameCardEntrance = false;
    }

    /** Keep filter labels synchronized with the real catalog and local job state. */
    private void updateLibraryFilterCounts() {
        if (libraryFilterButtons.isEmpty() || cachedGames == null || !isAdded()) return;
        int total = 0;
        int installed = 0;
        int ready = 0;
        int downloading = 0;
        android.content.SharedPreferences prefs = requireContext().getApplicationContext()
                .getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
        String activeJobId = prefs.getString(JOB_ID_KEY, "");
        String activeJobApp = prefs.getString(ACTIVE_JOB_APP_KEY, "");
        for (int i = 0; i < cachedGames.length(); i++) {
            JSONObject game = cachedGames.optJSONObject(i);
            if (game == null || game.optBoolean("is_dlc", false)) continue;
            String appName = game.optString("app_name", "");
            total++;
            if (!activeJobId.isEmpty() && appName.equals(activeJobApp)) {
                downloading++;
            } else if (installedApps.contains(appName)) {
                installed++;
            } else if (isGameInstallSupported(game)) {
                ready++;
            }
        }
        int[] counts = {total, installed, ready, downloading};
        String[] names = {"All games", "Installed", "Ready to install", "Downloading"};
        for (int i = 0; i < names.length; i++) {
            Button button = libraryFilterButtons.get(names[i]);
            if (button == null) continue;
            button.setText(names[i] + "  " + counts[i]);
            button.setContentDescription(names[i] + ", " + counts[i] + " games");
            boolean selected = names[i].equals(libraryFilter);
            button.setBackground(shape(selected ? 0xff5b55d9 : 0xff252f43,
                    selected ? 0xff5b55d9 : 0xff35425a, 11));
            button.setTextColor(selected ? 0xffffffff : 0xffd0d7e5);
        }
    }

    private int addGameShelf(String heading, String mode, String needle) {
        if (gamesContainer == null || cachedGames == null) return 0;
        android.content.SharedPreferences jobPrefs = requireContext().getApplicationContext()
                .getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
        String activeJobId = jobPrefs.getString(JOB_ID_KEY, "");
        String activeJobApp = jobPrefs.getString(ACTIVE_JOB_APP_KEY, "");
        JSONArray recentlyLaunched = getRecentlyLaunchedApps();
        LinearLayout shelf = new LinearLayout(requireContext());
        shelf.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView rail = new HorizontalScrollView(requireContext());
        rail.setHorizontalScrollBarEnabled(false);
        rail.setClipToPadding(false);
        rail.setFillViewport(false);
        rail.setPadding(dp(1), dp(2), dp(4), dp(12));
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        rail.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
        List<JSONObject> shelfGames = new ArrayList<>();
        if ("recent".equals(mode)) {
            for (int i = 0; i < recentlyLaunched.length(); i++) {
                String recentApp = recentlyLaunched.optString(i, "");
                for (int j = 0; j < cachedGames.length(); j++) {
                    JSONObject candidate = cachedGames.optJSONObject(j);
                    if (candidate != null && recentApp.equals(candidate.optString("app_name", ""))) {
                        shelfGames.add(candidate);
                        break;
                    }
                }
            }
        } else {
            for (int i = 0; i < cachedGames.length(); i++) {
                JSONObject candidate = cachedGames.optJSONObject(i);
                if (candidate != null) shelfGames.add(candidate);
            }
        }
        int count = 0;
        for (int i = 0; i < shelfGames.size(); i++) {
            JSONObject game = shelfGames.get(i);
            if (game == null || game.optBoolean("is_dlc", false)) continue;
            String appName = game.optString("app_name", "");
            String title = game.optString("title", appName);
            boolean installed = installedApps.contains(appName);
            boolean supported = isGameInstallSupported(game);
            boolean downloading = !activeJobId.isEmpty() && appName.equals(activeJobApp);
            if (!needle.isEmpty() && !title.toLowerCase(java.util.Locale.ROOT).contains(needle)) continue;
            if ("recent".equals(mode) && !installed) continue;
            if ("installed".equals(mode) && !installed) continue;
            if ("ready".equals(mode) && (installed || downloading || !supported)) continue;
            if ("downloading".equals(mode) && !downloading) continue;
            if ("all".equals(mode) && (installed || downloading || supported)) continue;
            View card = createGameCard(game);
            // Wider artwork cards give the shelf more of a storefront-cover
            // feel while keeping a useful number of titles reachable by swipe.
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(174), -2);
            params.rightMargin = dp(12);
            row.addView(card, params);
            count++;
        }
        if (count > 0) {
            LinearLayout shelfHeader = new LinearLayout(requireContext());
            shelfHeader.setOrientation(LinearLayout.HORIZONTAL);
            shelfHeader.setGravity(Gravity.CENTER_VERTICAL);
            shelfHeader.setPadding(dp(1), dp(13), dp(1), dp(9));
            TextView shelfTitle = label(heading, 10, 0xffc9c5ff, true);
            shelfTitle.setLetterSpacing(0.10f);
            shelfHeader.addView(shelfTitle, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView itemCount = label(String.valueOf(count), 9, 0xffaeb8cc, true);
            itemCount.setGravity(Gravity.CENTER);
            itemCount.setPadding(dp(8), dp(4), dp(8), dp(4));
            itemCount.setBackground(shape(0xff20283a, 0xff35425a, 20));
            itemCount.setContentDescription(count + (count == 1 ? " game" : " games"));
            shelfHeader.addView(itemCount);
            String destinationFilter = "downloading".equals(mode) ? "Downloading"
                    : ("installed".equals(mode) || "recent".equals(mode)) ? "Installed"
                    : "ready".equals(mode) ? "Ready to install" : "All games";
            TextView seeAll = label("SEE ALL  →", 8, 0xffd9d5ff, true);
            seeAll.setGravity(Gravity.CENTER);
            seeAll.setPadding(dp(10), dp(8), dp(2), dp(8));
            seeAll.setFocusable(true);
            seeAll.setClickable(true);
            seeAll.setContentDescription("See all " + heading.toLowerCase(java.util.Locale.ROOT));
            seeAll.setOnClickListener(v -> {
                libraryFilter = destinationFilter;
                renderGameCards(searchField == null ? "" : searchField.getText().toString());
                if (storefrontScroll != null) storefrontScroll.post(() -> {
                    View target = gamesContainer;
                    if (target != null) storefrontScroll.smoothScrollTo(0, target.getTop());
                });
            });
            shelfHeader.addView(seeAll);
            shelf.addView(shelfHeader);
            shelf.addView(rail, new LinearLayout.LayoutParams(-1, -2));
            gamesContainer.addView(shelf, new LinearLayout.LayoutParams(-1, -2));
        }
        return count;
    }

    private void renderFeaturedHero() {
        if (featuredContainer == null || !isAdded()) return;
        featuredContainer.removeAllViews();
        JSONObject featured = null;
        JSONArray recentlyLaunched = getRecentlyLaunchedApps();
        String lastOpenedApp = recentlyLaunched.optString(0, "");
        // Prefer the player's most recently launched installed title; otherwise
        // surface another installed game before falling back to Epic's catalog order.
        for (int i = 0; i < cachedGames.length(); i++) {
            JSONObject candidate = cachedGames.optJSONObject(i);
            if (candidate != null && !candidate.optBoolean("is_dlc", false)
                    && installedApps.contains(candidate.optString("app_name", ""))
                    && lastOpenedApp.equals(candidate.optString("app_name", ""))) {
                featured = candidate;
                break;
            }
        }
        if (featured == null) {
            for (int i = 0; i < cachedGames.length(); i++) {
                JSONObject candidate = cachedGames.optJSONObject(i);
                if (candidate != null && !candidate.optBoolean("is_dlc", false)
                        && installedApps.contains(candidate.optString("app_name", ""))) {
                    featured = candidate;
                    break;
                }
            }
        }
        if (featured == null) {
            for (int i = 0; i < cachedGames.length(); i++) {
                JSONObject candidate = cachedGames.optJSONObject(i);
                if (candidate != null && !candidate.optBoolean("is_dlc", false)) {
                    featured = candidate;
                    break;
                }
            }
        }
        if (featured == null) {
            LinearLayout empty = new LinearLayout(requireContext());
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(dp(18), dp(18), dp(18), dp(18));
            empty.setBackground(shape(0xff171d29, 0xff303b50, 18));
            empty.addView(label("YOUR NEXT ADVENTURE", 10, 0xffaaa6ff, true));
            addMargin(empty, label(libraryLoaded ? "Your library is waiting." : "Discover your Epic library.", 20, 0xfff5f6fa, true), 8);
            addMargin(empty, label(libraryLoaded ? "No games are available to feature yet." : "Sign in and refresh to bring your games here.", 12, 0xffaeb8c9, false), 5);
            featuredContainer.addView(empty);
            return;
        }
        final JSONObject featuredGame = featured;
        String appName = featuredGame.optString("app_name", "");
        String title = featuredGame.optString("title", appName);
        boolean installed = installedApps.contains(appName);
        android.content.SharedPreferences jobPrefs = requireContext().getApplicationContext()
                .getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
        boolean downloading = !jobPrefs.getString(JOB_ID_KEY, "").isEmpty()
                && appName.equals(jobPrefs.getString(ACTIVE_JOB_APP_KEY, ""));
        FrameLayout hero = new FrameLayout(requireContext());
        hero.setBackground(shape(0xff20283a, 0xff4a5272, 20));
        hero.setClipToOutline(true);
        ImageView image = new ImageView(requireContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable fallback = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{artColor(appName, 0), artColor(appName, 1), 0xff090d16});
        image.setBackground(fallback);
        hero.addView(image, new FrameLayout.LayoutParams(-1, -1));
        String coverUrl = featuredGame.optString("hero_url",
                featuredGame.optString("cover_url", ""));
        if (isTrustedEpicArtworkUrl(coverUrl)) {
            Glide.with(this).load(coverUrl).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(fallback).error(fallback).centerCrop().into(image);
        }
        View shade = new View(requireContext());
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00070a12, 0x66070a12, 0xf2070a12}));
        hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.BOTTOM);
        content.setPadding(dp(20), dp(18), dp(20), dp(18));
        hero.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(label("✦  FEATURED FROM YOUR LIBRARY", 9, 0xffe1ddff, true));
        TextView name = label(title, 27, 0xffffffff, true);
        name.setMaxLines(2);
        name.setShadowLayer(dp(8), 0, dp(2), 0xaa000000);
        addMargin(content, name, 6);
        addMargin(content, label(downloading ? "DOWNLOAD OR INSTALL IN PROGRESS" :
                (installed ? "INSTALLED  ·  READY TO PLAY" : "EPIC GAMES STORE"), 10,
                downloading ? 0xffffd18a : (installed ? 0xffa9f2c9 : 0xffc6c9d6), true), 4);
        boolean supported = isGameInstallSupported(featuredGame);
        Button explore = action(downloading ? "VIEW PROGRESS  →" :
                (installed ? "PLAY NOW  →" : (supported ? "INSTALL GAME  →" : "VIEW DETAILS  →")), true);
        explore.setTextSize(11);
        explore.setMinHeight(dp(40));
        explore.setOnClickListener(v -> {
            if (isActiveJobForGame(appName)) {
                showGameDetails(featuredGame);
            } else if (installed) {
                launchGame(appName, title);
            } else if (supported && storageReady) {
                installGame(appName, title);
            } else {
                showGameDetails(featuredGame);
            }
        });
        Button details = action("GAME DETAILS", false);
        details.setTextSize(10);
        details.setMinHeight(dp(42));
        details.setOnClickListener(v -> showGameDetails(featuredGame));
        LinearLayout heroActions = new LinearLayout(requireContext());
        heroActions.setOrientation(LinearLayout.HORIZONTAL);
        heroActions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams primaryActionParams =
                new LinearLayout.LayoutParams(0, dp(42), 1f);
        primaryActionParams.rightMargin = dp(8);
        heroActions.addView(explore, primaryActionParams);
        LinearLayout.LayoutParams detailsActionParams =
                new LinearLayout.LayoutParams(-2, dp(42));
        heroActions.addView(details, detailsActionParams);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(42));
        buttonParams.topMargin = dp(12);
        content.addView(heroActions, buttonParams);
        // Give the featured title enough vertical room to feel like the
        // storefront's primary destination on a phone-sized display.
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, dp(356));
        heroParams.bottomMargin = dp(8);
        featuredContainer.addView(hero, heroParams);
        if (!hasAnimatedFeaturedHero) {
            hasAnimatedFeaturedHero = true;
            hero.setAlpha(0f);
            hero.setTranslationY(dp(10));
            hero.animate().alpha(1f).translationY(0f).setDuration(280).start();
        } else {
            hero.setAlpha(1f);
            hero.setTranslationY(0f);
        }
    }

    /** A compact, data-backed snapshot gives the collection a clear second
     * level of hierarchy beneath the featured artwork. Counts come only from
     * the current Epic catalog and local install/job state. */
    private void renderLibraryStats() {
        if (featuredContainer == null || !isAdded()) return;
        int total = 0;
        int installed = 0;
        int ready = 0;
        for (int i = 0; i < cachedGames.length(); i++) {
            JSONObject game = cachedGames.optJSONObject(i);
            if (game == null || game.optBoolean("is_dlc", false)) continue;
            String appName = game.optString("app_name", "");
            total++;
            if (installedApps.contains(appName)) installed++;
            else if (isGameInstallSupported(game)) ready++;
        }
        LinearLayout stats = new LinearLayout(requireContext());
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        String[] values = {String.valueOf(total), String.valueOf(installed), String.valueOf(ready)};
        String[] captions = {"IN YOUR LIBRARY", "INSTALLED", "READY TO INSTALL"};
        int[] accents = {0xffc9c5ff, 0xffa9f2c9, 0xffffd18a};
        for (int i = 0; i < values.length; i++) {
            LinearLayout tile = new LinearLayout(requireContext());
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(12), dp(10), dp(8), dp(10));
            tile.setBackground(shape(0xff151b28, 0xff303b50, 13));
            TextView value = label(values[i], 21, accents[i], true);
            tile.addView(value);
            TextView caption = label(captions[i], 8, 0xff9ca8bf, true);
            caption.setLetterSpacing(0.035f);
            LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, -2);
            captionParams.topMargin = dp(3);
            tile.addView(caption, captionParams);
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) tileParams.leftMargin = dp(7);
            stats.addView(tile, tileParams);
        }
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-1, -2);
        statsParams.topMargin = dp(3);
        statsParams.bottomMargin = dp(7);
        featuredContainer.addView(stats, statsParams);
    }

    private View createGameCard(JSONObject game) {
        String appName = game.optString("app_name", "");
        String title = game.optString("title", appName);
        boolean installed = installedApps.contains(appName);
        android.content.SharedPreferences jobPrefs = requireContext().getApplicationContext()
                .getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
        boolean downloading = !jobPrefs.getString(JOB_ID_KEY, "").isEmpty()
                && appName.equals(jobPrefs.getString(ACTIVE_JOB_APP_KEY, ""));
        boolean supported = isGameInstallSupported(game);

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(11));
        card.setBackground(shape(0xff151b28, 0xff3b4660, 17));
        card.setElevation(dp(4));
        card.setClipToOutline(true);

        FrameLayout artwork = new FrameLayout(requireContext());
        artwork.setBackground(shape(0xff20283a, 0xff20283a, 12));
        artwork.setClipToOutline(true);
        ImageView cover = new ImageView(requireContext());
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable fallback = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{artColor(appName, 0), artColor(appName, 1), 0xff0b101b});
        cover.setBackground(fallback);
        artwork.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        // Compact library tiles use Epic's portrait box art. Keep wide offer
        // artwork for the featured hero and the immersive game profile.
        String coverUrl = game.optString("cover_url", "");
        if (coverUrl.isEmpty()) coverUrl = game.optString("hero_url", "");
        if (isTrustedEpicArtworkUrl(coverUrl)) {
            Glide.with(this).load(coverUrl).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(fallback).error(fallback).centerCrop().into(cover);
        } else {
            TextView initials = label(gameMonogram(title), 34, 0xfff5f6fa, true);
            initials.setGravity(Gravity.CENTER);
            artwork.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        }

        View shade = new View(requireContext());
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x18060a12, 0x00060a12, 0xe8060a12}));
        artwork.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        String status = downloading ? "DOWNLOADING" : (installed ? "INSTALLED" :
                (supported ? "READY TO INSTALL" : "IN YOUR LIBRARY"));
        int statusColor = downloading ? 0xffffd18a :
                (installed ? 0xffa9f2c9 : 0xffe0dcff);
        TextView tag = label(status, 8, statusColor, true);
        tag.setLetterSpacing(0.05f);
        tag.setPadding(dp(8), dp(6), dp(8), dp(6));
        tag.setBackground(shape(0xe51a2232, 0xe5515d77, 8));
        FrameLayout.LayoutParams tagParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.LEFT);
        tagParams.setMargins(dp(8), dp(8), 0, 0);
        artwork.addView(tag, tagParams);

        TextView open = label("DETAILS  ↗", 8, 0xffffffff, true);
        open.setLetterSpacing(0.05f);
        open.setPadding(dp(9), dp(6), dp(9), dp(6));
        open.setBackground(shape(0xb8101520, 0x995e6a84, 8));
        FrameLayout.LayoutParams openParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.RIGHT);
        openParams.setMargins(0, 0, dp(8), dp(8));
        artwork.addView(open, openParams);

        // Active installs remain visually identifiable while the user browses
        // other shelves. This is intentionally indeterminate: exact percentage
        // belongs to the persisted worker snapshot and is shown on the profile,
        // not fabricated or inferred from elapsed time.
        if (downloading) {
            ProgressBar activity = new ProgressBar(requireContext(), null,
                    android.R.attr.progressBarStyleHorizontal);
            activity.setIndeterminate(true);
            activity.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xffaaa6ff));
            FrameLayout.LayoutParams activityParams = new FrameLayout.LayoutParams(
                    -1, dp(3), Gravity.BOTTOM);
            activityParams.setMargins(dp(8), 0, dp(8), dp(7));
            artwork.addView(activity, activityParams);
        }

        // Epic box art is portrait-oriented (roughly 2:3); avoid cropping it
        // into the old landscape tile shape.
        card.addView(artwork, new LinearLayout.LayoutParams(-1, dp(232)));

        TextView name = label(title, 13, 0xfff5f6fa, true);
        name.setMaxLines(2);
        name.setMinLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(dp(3), dp(11), dp(3), dp(4));
        card.addView(name);

        TextView description = label(downloading ? "Download or installation in progress" :
                (installed ? "Ready to play" :
                (supported ? "Available to install" : "Catalog entry")),
                10, downloading ? 0xffffd18a :
                        (installed ? 0xff83dfad : 0xffaab5ca), false);
        description.setMaxLines(1);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        description.setPadding(dp(3), 0, dp(3), 0);
        card.addView(description);

        card.setContentDescription(title + ". " + status.toLowerCase(java.util.Locale.ROOT)
                + ". Open game details.");
        card.setOnClickListener(v -> {
            card.animate().scaleX(0.965f).scaleY(0.965f).setDuration(90)
                    .withEndAction(() -> {
                        card.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                        showGameDetails(game);
                    }).start();
        });
        card.setClickable(true);
        card.setFocusable(true);
        if (animateGameCardEntrance) {
            card.setAlpha(0f);
            card.setTranslationY(dp(12));
            card.animate().alpha(1f).translationY(0f).setDuration(300).start();
        } else {
            card.setAlpha(1f);
            card.setTranslationY(0f);
        }
        return card;
    }

    private JSONObject findCachedGame(String appName) {
        if (appName == null || appName.isEmpty()) return selectedGame;
        for (int i = 0; i < cachedGames.length(); i++) {
            JSONObject item = cachedGames.optJSONObject(i);
            if (item != null && appName.equals(item.optString("app_name"))) return item;
        }
        return selectedGame;
    }

    private void updateGameActionButton(String text, boolean enabled) {
        if (gamePrimaryButton == null) return;
        gamePrimaryButton.setText(text);
        gamePrimaryButton.setEnabled(enabled);
        gamePrimaryButton.setAlpha(enabled ? 1.0f : 0.72f);
    }

    private boolean isGameInstallSupported(JSONObject game) {
        return game != null && !game.optString("version", "").isEmpty()
                && game.optString("third_party_store", "").isEmpty();
    }

    private boolean isActiveJobForGame(String appName) {
        if (appName == null || appName.isEmpty() || getContext() == null) return false;
        android.content.SharedPreferences prefs = requireContext().getApplicationContext()
                .getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE);
        return !prefs.getString(JOB_ID_KEY, "").isEmpty()
                && appName.equals(prefs.getString(ACTIVE_JOB_APP_KEY, ""));
    }

    private void returnToLibraryFromProfile() {
        if (pageContainer == null || !showingGameDetails) {
            showSection("library");
            return;
        }
        View current = pageContainer.getChildCount() > 0 ? pageContainer.getChildAt(0) : null;
        if (current == null) {
            showSection("library");
            return;
        }
        current.animate().alpha(0f).translationY(dp(12)).setDuration(130)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    showSection("library");
                    if (storefrontScroll != null) {
                        storefrontScroll.setAlpha(0f);
                        storefrontScroll.setTranslationY(dp(8));
                        storefrontScroll.animate().alpha(1f).translationY(0f).setDuration(210)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    }
                }).start();
    }

    private void showGameDetails(JSONObject game) {
        if (game == null || pageContainer == null) return;
        // Library refreshes may rebuild the visible profile after installed-state
        // reconciliation; preserve the reader's position in that case.
        final int previousProfileScrollY = showingGameDetails && profileScroll != null
                ? profileScroll.getScrollY() : 0;
        selectedGame = game;
        showingGameDetails = true;
        String appName = game.optString("app_name", "");
        selectedGameAppName = appName;
        String title = game.optString("title", appName);
        boolean installed = installedApps.contains(appName);
        boolean supported = isGameInstallSupported(game);
        boolean gameJobForThisTitle = isActiveJobForGame(appName);

        if (storefrontScroll != null) storefrontScrollY = storefrontScroll.getScrollY();
        if (brandHeader != null) brandHeader.setVisibility(View.GONE);
        if (navigationBar != null) navigationBar.setVisibility(View.GONE);
        pageContainer.removeAllViews();
        LinearLayout detail = new LinearLayout(requireContext());
        detail.setOrientation(LinearLayout.VERTICAL);
        profileScroll = new ScrollView(requireContext());
        profileScroll.setFillViewport(true);
        profileScroll.setClipToPadding(false);
        // Reserve space for the persistent profile action dock so the final
        // metadata cards can scroll fully above it instead of disappearing behind it.
        profileScroll.setPadding(0, 0, 0, dp(82));
        profileScroll.addView(detail);
        FrameLayout profileRoot = new FrameLayout(requireContext());
        profileRoot.addView(profileScroll, new FrameLayout.LayoutParams(-1, -1));
        pageContainer.addView(profileRoot, new LinearLayout.LayoutParams(-1, -1));
        detail.setAlpha(0f);
        detail.setTranslationY(dp(22));
        detail.setScaleX(0.985f);
        detail.setScaleY(0.985f);
        detail.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(280).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        // A newly opened profile starts at its artwork; background reconciliation
        // keeps the existing profile position instead of jumping the user to the top.
        if (profileScroll != null) {
            profileScroll.post(() -> profileScroll.scrollTo(0, Math.max(0, previousProfileScrollY)));
        }

        FrameLayout artwork = new FrameLayout(requireContext());
        GradientDrawable art = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{artColor(appName, 0), artColor(appName, 1), 0xff111827});
        art.setCornerRadius(dp(16));
        ImageView cover = new ImageView(requireContext());
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(art);
        artwork.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        // Profiles prioritize the wide cinematic offer image; portrait box art
        // remains a fallback for catalog entries that do not publish a hero image.
        String coverUrl = game.optString("hero_url", "");
        if (coverUrl.isEmpty()) coverUrl = game.optString("cover_url", "");
        if (isTrustedEpicArtworkUrl(coverUrl)) {
            Glide.with(this).load(coverUrl).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(art).error(art).centerCrop().into(cover);
        } else {
            TextView initials = label(gameMonogram(title), 42, 0xfff5f6fa, true);
            initials.setGravity(Gravity.CENTER);
            artwork.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        }
        View detailShade = new View(requireContext());
        detailShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00070a12, 0x33070a12, 0xe6070a12}));
        artwork.addView(detailShade, new FrameLayout.LayoutParams(-1, -1));
        Button back = action("‹  LIBRARY", false);
        back.setTextSize(11);
        back.setMinHeight(dp(38));
        back.setPadding(dp(12), 0, dp(12), 0);
        back.setBackground(shape(0xb8141b29, 0x665e6a84, 18));
        back.setOnClickListener(v -> returnToLibraryFromProfile());
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(-2, dp(38),
                Gravity.TOP | Gravity.LEFT);
        backParams.setMargins(dp(14), dp(14), 0, 0);
        artwork.addView(back, backParams);
        LinearLayout artworkCaption = new LinearLayout(requireContext());
        artworkCaption.setOrientation(LinearLayout.VERTICAL);
        artworkCaption.setPadding(dp(17), dp(12), dp(17), dp(16));
        artworkCaption.addView(label("EPIC GAMES  /  GAME PROFILE", 9, 0xffdedbff, true));
        TextView artworkTitle = label(title, 27, 0xffffffff, true);
        artworkTitle.setMaxLines(2);
        artworkTitle.setShadowLayer(dp(8), 0, dp(2), 0xaa000000);
        addMargin(artworkCaption, artworkTitle, 5);
        artworkCaption.addView(label(installed ? "INSTALLED  •  READY TO PLAY" :
                (supported ? "IN YOUR LIBRARY  •  READY TO INSTALL" : "IN YOUR EPIC LIBRARY"),
                9, installed ? 0xffa9f2c9 : 0xffd8d4ff, true));
        FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.LEFT);
        captionParams.setMargins(dp(16), 0, dp(16), dp(14));
        artwork.addView(artworkCaption, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(-1, dp(430));
        artParams.topMargin = dp(4);
        detail.addView(artwork, artParams);


        gamePrimaryButton = action(gameJobForThisTitle ? "Cancel download" :
                (installed ? "Play game" : (supported ? "Install" : "Unavailable")), true);
        // The primary action is moved into a persistent bottom dock below. It
        // remains reachable while achievements and account metadata are scrolled.
        gamePrimaryButton.setEnabled(installed || (supported && storageReady) || gameJobForThisTitle);
        gamePrimaryButton.setOnClickListener(v -> {
            gamePrimaryButton.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                    .withEndAction(() -> gamePrimaryButton.animate().scaleX(1.0f).scaleY(1.0f)
                            .setDuration(110).start()).start();
            if (isActiveJobForGame(appName)) {
                updateGameActionButton("Cancelling...", false);
                cancelActiveJob();
            } else if (installed) {
                launchGame(appName, title);
            } else if (supported) {
                installGame(appName, title);
            }
        });

        LinearLayout actionDock = new LinearLayout(requireContext());
        actionDock.setOrientation(LinearLayout.HORIZONTAL);
        actionDock.setGravity(Gravity.CENTER_VERTICAL);
        actionDock.setPadding(dp(14), dp(10), dp(14), dp(10));
        actionDock.setBackground(shape(0xf0141b29, 0xff35415a, 0));
        LinearLayout dockCopy = new LinearLayout(requireContext());
        dockCopy.setOrientation(LinearLayout.VERTICAL);
        dockCopy.setGravity(Gravity.CENTER_VERTICAL);
        TextView dockTitle = label(title, 12, 0xfff5f6fa, true);
        dockTitle.setMaxLines(1);
        dockTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dockCopy.addView(dockTitle);
        String dockStatus = gameJobForThisTitle ? "INSTALLATION IN PROGRESS"
                : installed ? "READY TO PLAY"
                : supported ? "READY TO INSTALL" : "EPIC LIBRARY";
        TextView dockSubtitle = label(dockStatus, 8,
                gameJobForThisTitle ? 0xffffd18a : installed ? 0xffa9f2c9 : 0xffaeb8c9, true);
        dockSubtitle.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams dockSubtitleParams = new LinearLayout.LayoutParams(-1, -2);
        dockSubtitleParams.topMargin = dp(3);
        dockCopy.addView(dockSubtitle, dockSubtitleParams);
        actionDock.addView(dockCopy, new LinearLayout.LayoutParams(0, -2, 1f));
        gamePrimaryButton.setTextSize(12);
        gamePrimaryButton.setMinHeight(dp(44));
        LinearLayout.LayoutParams dockButtonParams = new LinearLayout.LayoutParams(dp(142), dp(46));
        dockButtonParams.leftMargin = dp(10);
        actionDock.addView(gamePrimaryButton, dockButtonParams);
        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(-1, -2,
                Gravity.BOTTOM);
        profileRoot.addView(actionDock, dockParams);

        LinearLayout progressCard = new LinearLayout(requireContext());
        progressCard.setOrientation(LinearLayout.VERTICAL);
        progressCard.setPadding(dp(14), dp(13), dp(14), dp(13));
        progressCard.setBackground(shape(0xff171d29, 0xff303b50, 13));
        progressCard.addView(label("DOWNLOAD STATUS", 10, 0xffaaa6ff, true));
        gameProgressText = label(gameJobForThisTitle ? lastProgressLabel :
                (installed ? "Game is installed and ready." : "Install this game to see live progress here."),
                12, 0xffdce3f2, true);
        addMargin(progressCard, gameProgressText, 8);
        gameProgressBar = new ProgressBar(requireContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        gameProgressBar.setMax(100);
        gameProgressBar.setIndeterminate(gameJobForThisTitle);
        gameProgressBar.setProgress(0);
        addMargin(progressCard, gameProgressBar, 10);
        // Live worker progress is part of the hero action area, not a separate
        // panel below the artwork. The same persisted job observer still drives it.
        addMargin(artworkCaption, progressCard, 9);

        // Keep player-facing metadata in separate cards so each feature has a
        // clear status and can grow independently without one dense information panel.
        LinearLayout achievementsCard = new LinearLayout(requireContext());
        achievementsCard.setOrientation(LinearLayout.VERTICAL);
        achievementsCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        achievementsCard.setBackground(shape(0xff171d29, 0xff35415a, 13));
        achievementsCard.addView(label("ACHIEVEMENTS", 10, 0xffaaa6ff, true));
        addMargin(achievementsCard, label("Your Epic progress", 15, 0xfff5f6fa, true), 7);
        TextView achievementsValue = label("Checking Epic achievement data...", 11, 0xffaeb8cb, false);
        addMargin(achievementsCard, achievementsValue, 4);
        ProgressBar achievementsCompletion = new ProgressBar(requireContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        achievementsCompletion.setMax(100);
        achievementsCompletion.setProgress(0);
        addMargin(achievementsCard, achievementsCompletion, 9);
        HorizontalScrollView achievementsRail = new HorizontalScrollView(requireContext());
        achievementsRail.setHorizontalScrollBarEnabled(false);
        achievementsRail.setClipToPadding(false);
        achievementsRail.setFillViewport(false);
        achievementsRail.setPadding(0, dp(2), dp(2), dp(8));
        LinearLayout achievementsList = new LinearLayout(requireContext());
        achievementsList.setOrientation(LinearLayout.HORIZONTAL);
        achievementsRail.addView(achievementsList,
                new HorizontalScrollView.LayoutParams(-2, -2));
        addMargin(achievementsCard, achievementsRail, 10);
        addMargin(detail, achievementsCard, 12);

        // Compact two-column account-stat row gives the profile a deliberate
        // storefront hierarchy without inventing playtime or cloud-sync support.
        LinearLayout accountInfoRow = new LinearLayout(requireContext());
        accountInfoRow.setOrientation(LinearLayout.HORIZONTAL);
        accountInfoRow.setGravity(Gravity.TOP);
        accountInfoRow.setBaselineAligned(false);

        LinearLayout playtimeCard = new LinearLayout(requireContext());
        playtimeCard.setOrientation(LinearLayout.VERTICAL);
        playtimeCard.setPadding(dp(13), dp(14), dp(13), dp(14));
        playtimeCard.setBackground(shape(0xff171d29, 0xff293247, 14));
        playtimeCard.addView(label("PLAYTIME", 9, 0xffaaa6ff, true));
        addMargin(playtimeCard, label("Not available", 15, 0xfff5f6fa, true), 8);
        addMargin(playtimeCard, label("Epic does not return playtime for this title.", 10,
                0xff929db1, false), 4);

        LinearLayout cloudSavesCard = new LinearLayout(requireContext());
        cloudSavesCard.setOrientation(LinearLayout.VERTICAL);
        cloudSavesCard.setPadding(dp(13), dp(14), dp(13), dp(14));
        cloudSavesCard.setBackground(shape(0xff171d29, 0xff293247, 14));
        cloudSavesCard.addView(label("CLOUD SAVES", 9, 0xffaaa6ff, true));
        addMargin(cloudSavesCard, label("Support status", 15, 0xfff5f6fa, true), 8);
        TextView cloudSavesValue = label("Checking title metadata...", 10, 0xff929db1, false);
        addMargin(cloudSavesCard, cloudSavesValue, 4);
        addMargin(cloudSavesCard, label("No save transfer is performed.", 9,
                0xff929db1, false), 7);

        LinearLayout.LayoutParams statParams = new LinearLayout.LayoutParams(0, -1, 1f);
        statParams.rightMargin = dp(5);
        accountInfoRow.addView(playtimeCard, statParams);
        LinearLayout.LayoutParams cloudParams = new LinearLayout.LayoutParams(0, -1, 1f);
        cloudParams.leftMargin = dp(5);
        accountInfoRow.addView(cloudSavesCard, cloudParams);
        addMargin(detail, accountInfoRow, 9);

        if (installed) {
            Button manage = action("Update / Uninstall", false);
            manage.setOnClickListener(v -> showInstalledActions(appName, title));
            addMargin(detail, manage, 10);
        }
        if (!storageReady && !installed) {
            TextView permission = label("Grant storage access in Account & setup before installing.", 11, 0xffffc978, false);
            addMargin(detail, permission, 9);
        }

        JSONObject detailsPayload = new JSONObject();
        try { detailsPayload.put("app_name", appName); } catch (Exception ignored) {}
        runCommand("game_details", detailsPayload.toString(), result -> {
            if (getView() == null || !appName.equals(selectedGameAppName)) return;
            if (!result.optBoolean("ok", false)) {
                achievementsValue.setText("Epic achievement data is currently unavailable.");
                cloudSavesValue.setText("Unable to check cloud-save status.");
                return;
            }
            JSONObject metadata = result.optJSONObject("game");
            if (metadata == null) {
                achievementsValue.setText("Epic did not return game details.");
                cloudSavesValue.setText("Cloud-save status is unavailable.");
                return;
            }
            JSONObject achievements = metadata.optJSONObject("achievements");
            achievementsList.removeAllViews();
            if (achievements != null && achievements.optBoolean("available", false)) {
                int total = achievements.optInt("total_achievements", 0);
                int unlocked = achievements.optInt("user_unlocked", 0);
                int xp = achievements.optInt("user_xp", 0);
                int totalXp = achievements.optInt("total_product_xp", 0);
                achievementsValue.setText("Unlocked " + unlocked + " / " + total + " • "
                        + xp + " / " + totalXp + " XP");
                achievementsCompletion.setProgress(total > 0
                        ? Math.max(0, Math.min(100, Math.round(unlocked * 100f / total))) : 0);
                org.json.JSONArray items = achievements.optJSONArray("items");
                if (items != null) {
                    int shown = Math.min(items.length(), 30);
                    for (int index = 0; index < shown; index++) {
                        JSONObject item = items.optJSONObject(index);
                        if (item == null) continue;
                        String name = item.optString("display_name", "Achievement");
                        if (name.trim().isEmpty()) name = "Achievement";
                        LinearLayout achievementCard = new LinearLayout(requireContext());
                        achievementCard.setOrientation(LinearLayout.VERTICAL);
                        achievementCard.setPadding(dp(10), dp(9), dp(10), dp(9));
                        achievementCard.setBackground(shape(0xff202838, 0xff303b50, 10));
                        LinearLayout achievementHeading = new LinearLayout(requireContext());
                        achievementHeading.setOrientation(LinearLayout.HORIZONTAL);
                        achievementHeading.setGravity(Gravity.CENTER_VERTICAL);
                        FrameLayout iconFrame = new FrameLayout(requireContext());
                        iconFrame.setBackground(shape(0xff30394b, 0xff46536b, 9));
                        iconFrame.setClipToOutline(true);
                        ImageView achievementIcon = new ImageView(requireContext());
                        achievementIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        iconFrame.addView(achievementIcon, new FrameLayout.LayoutParams(-1, -1));
                        String iconUrl = item.optString("icon_link", "");
                        if (isTrustedEpicArtworkUrl(iconUrl)) {
                            Glide.with(this).load(iconUrl)
                                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                    .placeholder(shape(0xff30394b, 0xff46536b, 9))
                                    .error(shape(0xff30394b, 0xff46536b, 9))
                                    .centerCrop().into(achievementIcon);
                        } else {
                            TextView iconFallback = label("✦", 19, 0xffd8d4ff, true);
                            iconFallback.setGravity(Gravity.CENTER);
                            iconFrame.addView(iconFallback,
                                    new FrameLayout.LayoutParams(-1, -1));
                        }
                        achievementHeading.addView(iconFrame,
                                new LinearLayout.LayoutParams(dp(42), dp(42)));
                        TextView achievementTitle = label(name, 12, 0xfff5f6fa, true);
                        achievementTitle.setMaxLines(2);
                        LinearLayout.LayoutParams achievementTitleParams =
                                new LinearLayout.LayoutParams(0, -2, 1f);
                        achievementTitleParams.leftMargin = dp(9);
                        achievementHeading.addView(achievementTitle, achievementTitleParams);
                        achievementCard.addView(achievementHeading);
                        String description = item.optString("description", "");
                        if (!description.isEmpty()) {
                            TextView achievementDescription = label(description, 10, 0xffaeb8c9, false);
                            addMargin(achievementCard, achievementDescription, 3);
                        }
                        StringBuilder reward = new StringBuilder();
                        int achievementXp = item.optInt("xp", 0);
                        if (achievementXp > 0) reward.append(achievementXp).append(" XP");
                        String tier = item.optString("tier", "");
                        String rarity = item.optString("rarity", "");
                        if (!tier.isEmpty()) {
                            if (reward.length() > 0) reward.append(" • ");
                            reward.append(tier);
                        }
                        if (!rarity.isEmpty()) {
                            if (reward.length() > 0) reward.append(" • ");
                            reward.append(rarity);
                        }
                        if (reward.length() > 0) {
                            TextView rewardLabel = label(reward.toString(), 9, 0xff929db1, false);
                            addMargin(achievementCard, rewardLabel, 3);
                        }
                        boolean isUnlocked = item.optBoolean("unlocked", false);
                        boolean isHidden = item.optBoolean("hidden", false);
                        double progress = item.optDouble("progress", 0.0);
                        String state = isUnlocked ? "Unlocked"
                                : (isHidden ? "Hidden" : (progress > 0.0
                                ? "In progress • " + Math.round(progress * 100.0) + "%"
                                : "Locked"));
                        TextView achievementState = label(state, 10,
                                isUnlocked ? 0xff8ed6a7 : 0xffaeb8c9, true);
                        addMargin(achievementCard, achievementState, 5);
                        String unlockDate = item.optString("unlock_date", "");
                        if (isUnlocked && !unlockDate.isEmpty() && !"null".equals(unlockDate)) {
                            TextView unlockedAt = label("Unlocked: " + unlockDate, 9, 0xff929db1, false);
                            addMargin(achievementCard, unlockedAt, 2);
                        }
                        if (!isUnlocked && !isHidden && progress > 0.0) {
                            ProgressBar achievementProgress = new ProgressBar(requireContext(), null,
                                    android.R.attr.progressBarStyleHorizontal);
                            achievementProgress.setMax(100);
                            achievementProgress.setProgress((int) Math.round(progress * 100.0));
                            addMargin(achievementCard, achievementProgress, 4);
                        }
                        StringBuilder achievementDetails = new StringBuilder();
                        if (!description.isEmpty()) achievementDetails.append(description).append("\\n\\n");
                        achievementDetails.append(state);
                        if (achievementXp > 0) achievementDetails.append("\\n").append(achievementXp).append(" XP");
                        if (!tier.isEmpty()) achievementDetails.append(" • ").append(tier);
                        if (!rarity.isEmpty()) achievementDetails.append(" • ").append(rarity);
                        if (isUnlocked && !unlockDate.isEmpty() && !"null".equals(unlockDate)) {
                            achievementDetails.append("\\nUnlocked: ").append(unlockDate);
                        } else if (!isUnlocked && !isHidden && progress > 0.0) {
                            achievementDetails.append("\\nProgress: ")
                                    .append(Math.round(progress * 100.0)).append("%");
                        }
                        final String achievementDialogTitle = name;
                        achievementCard.setClickable(true);
                        achievementCard.setFocusable(true);
                        achievementCard.setContentDescription(achievementDialogTitle + ". " + state
                                + ". Tap for achievement details.");
                        achievementCard.setOnClickListener(v ->
                                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                        .setTitle(achievementDialogTitle)
                                        .setMessage(achievementDetails.toString())
                                        .setPositiveButton("Close", null)
                                        .show());
                        LinearLayout.LayoutParams achievementParams =
                                new LinearLayout.LayoutParams(dp(250), -2);
                        achievementParams.rightMargin = dp(9);
                        achievementsList.addView(achievementCard, achievementParams);
                    }
                }
                if (achievements.optBoolean("truncated", false) || (items != null && items.length() > 30)) {
                    TextView more = label("Showing the first 30 achievements.", 10, 0xff929db1, false);
                    addMargin(achievementsList, more, 4);
                }
                if (total == 0 && (items == null || items.length() == 0)) {
                    achievementsValue.setText("No Epic Store achievements are listed for this title.");
                }
            } else if (achievements != null && achievements.has("error")) {
                achievementsValue.setText("Epic achievement data is temporarily unavailable.");
            } else {
                achievementsValue.setText("No Epic Store achievement data was returned for this title.");
            }
            JSONObject cloud = metadata.optJSONObject("cloud_saves");
            if (cloud == null || !cloud.optBoolean("supported", false)) {
                cloudSavesValue.setText("Epic does not report Windows cloud-save support for this title.");
            } else if (!cloud.has("remote_available") || cloud.isNull("remote_available")) {
                cloudSavesValue.setText("Windows cloud saves are supported; remote save status could not be checked.");
            } else {
                int count = cloud.optInt("remote_count", 0);
                cloudSavesValue.setText(cloud.optBoolean("remote_available", false)
                        ? "Windows cloud saves supported • " + count + " remote save record(s) found. Sync is not enabled yet."
                        : "Windows cloud saves supported • no remote save records found. Sync is not enabled yet.");
            }
        });
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
        gameJobActive = true;
        android.content.Context appContext = requireContext().getApplicationContext();
        appContext.getSharedPreferences(JOB_PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putString(ACTIVE_JOB_APP_KEY, appName).apply();
        updateGameActionButton("Starting download...", false);
        if (gameProgressText != null) gameProgressText.setText("Preparing " + title + "...");
        runCommand("install", payload.toString(), result -> {
            if (!result.optBoolean("pending", false)) {
                gameJobActive = false;
                updateGameActionButton(installedApps.contains(appName) ? "Play game" : "Install", true);
            }
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
            recordLaunchedGame(appName);
        });
    }

    /** Most recently launched Epic titles, newest first, stored locally without account data. */
    private JSONArray getRecentlyLaunchedApps() {
        android.content.SharedPreferences prefs = requireContext().getApplicationContext()
                .getSharedPreferences("epic_storefront_ui", android.content.Context.MODE_PRIVATE);
        try {
            return new JSONArray(prefs.getString("recently_launched_apps_json", "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void recordLaunchedGame(String appName) {
        if (appName == null || appName.isEmpty() || getContext() == null) return;
        JSONArray previous = getRecentlyLaunchedApps();
        JSONArray updated = new JSONArray();
        updated.put(appName);
        for (int i = 0; i < previous.length() && updated.length() < 8; i++) {
            String candidate = previous.optString(i, "");
            if (!candidate.isEmpty() && !appName.equals(candidate)) updated.put(candidate);
        }
        requireContext().getApplicationContext()
                .getSharedPreferences("epic_storefront_ui", android.content.Context.MODE_PRIVATE)
                .edit().putString("recently_launched_apps_json", updated.toString()).apply();
        if (libraryLoaded && "library".equals(activeSection) && !showingGameDetails) {
            renderGameCards(searchField == null ? "" : searchField.getText().toString());
        }
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
