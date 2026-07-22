package com.lariflix.jemm.utils;

import com.lariflix.jemm.Jemm;
import com.lariflix.jemm.dtos.JellyfinCredentials;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Persists and loads Jellyfin login credentials (URL and API key)
 * using Java Preferences so they survive application restarts.
 */
public class JemmCredentialsStore {

    private static final String KEY_BASE_URL = "baseURL";
    private static final String KEY_TOKEN_API = "tokenAPI";

    private final Preferences prefs;

    public JemmCredentialsStore() {
        this.prefs = Preferences.userNodeForPackage(Jemm.class);
    }

    /**
     * Loads saved credentials. Returns empty strings when nothing is stored.
     *
     * @return credentials from Preferences, never null
     */
    public JellyfinCredentials load() {
        try {
            String baseURL = prefs.get(KEY_BASE_URL, "");
            String tokenAPI = prefs.get(KEY_TOKEN_API, "");
            return new JellyfinCredentials(baseURL, tokenAPI);
        } catch (Exception ex) {
            Logger.getLogger(JemmCredentialsStore.class.getName()).log(Level.WARNING, "Failed to load saved credentials", ex);
            return new JellyfinCredentials();
        }
    }

    /**
     * Saves credentials after a successful login.
     *
     * @param baseURL  Jellyfin server URL
     * @param tokenAPI Jellyfin API key
     */
    public void save(String baseURL, String tokenAPI) {
        try {
            prefs.put(KEY_BASE_URL, baseURL != null ? baseURL : "");
            prefs.put(KEY_TOKEN_API, tokenAPI != null ? tokenAPI : "");
            prefs.flush();
        } catch (BackingStoreException ex) {
            Logger.getLogger(JemmCredentialsStore.class.getName()).log(Level.WARNING, "Failed to save credentials", ex);
        } catch (Exception ex) {
            Logger.getLogger(JemmCredentialsStore.class.getName()).log(Level.WARNING, "Failed to save credentials", ex);
        }
    }
}
