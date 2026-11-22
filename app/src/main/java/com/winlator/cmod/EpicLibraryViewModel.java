package com.winlator.cmod;

import androidx.lifecycle.ViewModel;

import org.json.JSONArray;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Activity-scoped cache for the Epic library. MainActivity creates a new
 * EpicFragment when navigating between drawer destinations, so Fragment fields
 * alone cannot retain the loaded library across a Containers/Epic round trip.
 */
public final class EpicLibraryViewModel extends ViewModel {
    private JSONArray games = new JSONArray();
    private boolean loaded;
    private final Set<String> installedApps = new HashSet<>();
    private final Map<String, String> installedPaths = new HashMap<>();

    public synchronized JSONArray getGames() {
        return games;
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }

    public synchronized Set<String> getInstalledApps() {
        return new HashSet<>(installedApps);
    }

    public synchronized Map<String, String> getInstalledPaths() {
        return new HashMap<>(installedPaths);
    }

    public synchronized void setLibrary(JSONArray games, Set<String> installedApps,
                                        Map<String, String> installedPaths) {
        this.games = games == null ? new JSONArray() : games;
        this.installedApps.clear();
        if (installedApps != null) this.installedApps.addAll(installedApps);
        this.installedPaths.clear();
        if (installedPaths != null) this.installedPaths.putAll(installedPaths);
        loaded = true;
    }
}
