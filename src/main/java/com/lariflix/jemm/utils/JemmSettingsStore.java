package com.lariflix.jemm.utils;

import com.lariflix.jemm.Jemm;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Persists JEMM tool settings (currently the ffprobe fallback configuration)
 * using Java Preferences so they survive application restarts.
 */
public class JemmSettingsStore {

    private static final String KEY_FFPROBE_PATH = "ffprobePath";
    private static final String KEY_USE_FFPROBE_FALLBACK = "useFfprobeFallback";
    private static final String KEY_USE_ASPECT_HINT_FALLBACK = "usePosterAspectFallback";
    private static final String KEY_CATEGORY_PREFIX = "autoTagCategory_";

    private final Preferences prefs;

    public JemmSettingsStore() {
        this.prefs = Preferences.userNodeForPackage(Jemm.class);
    }

    /**
     * Returns the configured ffprobe executable path, or an empty string when
     * none is set (in which case the binary is expected on the system PATH).
     *
     * @return the stored ffprobe path, never null
     */
    public String getFfprobePath() {
        try {
            return prefs.get(KEY_FFPROBE_PATH, "");
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to read ffprobe path", ex);
            return "";
        }
    }

    /**
     * Stores the ffprobe executable path (empty to rely on PATH).
     *
     * @param path the ffprobe path, may be null/empty
     */
    public void setFfprobePath(String path) {
        try {
            prefs.put(KEY_FFPROBE_PATH, path != null ? path.trim() : "");
            prefs.flush();
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to save ffprobe path", ex);
        }
    }

    /**
     * Returns whether the ffprobe fallback is enabled (default true).
     *
     * @return true when Auto Tags may probe media with ffprobe
     */
    public boolean isUseFfprobeFallback() {
        try {
            return prefs.getBoolean(KEY_USE_FFPROBE_FALLBACK, true);
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to read ffprobe fallback flag", ex);
            return true;
        }
    }

    /**
     * Stores whether the ffprobe fallback is enabled.
     *
     * @param enabled true to allow ffprobe probing
     */
    public void setUseFfprobeFallback(boolean enabled) {
        try {
            prefs.putBoolean(KEY_USE_FFPROBE_FALLBACK, enabled);
            prefs.flush();
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to save ffprobe fallback flag", ex);
        }
    }

    /**
     * Returns whether the poster/aspect-ratio hint may be used to derive orientation
     * when ffprobe is unavailable (default false).
     *
     * @return true when the aspect-ratio hint fallback is enabled
     */
    public boolean isUsePosterAspectFallback() {
        try {
            return prefs.getBoolean(KEY_USE_ASPECT_HINT_FALLBACK, false);
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to read aspect hint flag", ex);
            return false;
        }
    }

    /**
     * Stores whether the poster/aspect-ratio hint fallback is enabled.
     *
     * @param enabled true to allow the aspect-ratio hint fallback
     */
    public void setUsePosterAspectFallback(boolean enabled) {
        try {
            prefs.putBoolean(KEY_USE_ASPECT_HINT_FALLBACK, enabled);
            prefs.flush();
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to save aspect hint flag", ex);
        }
    }

    /**
     * Returns whether an auto-tag category is enabled (default true).
     *
     * @param name          the category key (e.g. "orientation")
     * @param defaultValue  the value to use when nothing is stored
     * @return true when the category is enabled
     */
    public boolean isAutoTagCategoryEnabled(String name, boolean defaultValue) {
        try {
            return prefs.getBoolean(KEY_CATEGORY_PREFIX + name, defaultValue);
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to read category flag", ex);
            return defaultValue;
        }
    }

    /**
     * Stores whether an auto-tag category is enabled.
     *
     * @param name    the category key (e.g. "orientation")
     * @param enabled true to enable the category
     */
    public void setAutoTagCategoryEnabled(String name, boolean enabled) {
        try {
            prefs.putBoolean(KEY_CATEGORY_PREFIX + name, enabled);
            prefs.flush();
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to save category flag", ex);
        }
    }
}
