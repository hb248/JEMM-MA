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
    private static final String KEY_TAG_MAP_PATH = "tagTeamMapPath";
    private static final String KEY_TAG_TEAM_INCLUDE_VIDEOS = "tagTeamIncludeVideos";
    private static final String KEY_TAG_TEAM_INCLUDE_IMAGES = "tagTeamIncludeImages";
    private static final String KEY_TAG_TEAM_INCLUDE_OTHER = "tagTeamIncludeOther";
    private static final String KEY_TAG_TEAM_CASCADE_SUBFOLDERS = "tagTeamCascadeSubfolders";

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

    /**
     * Returns the file location of the single active Tag-Team tag map. Defaults to
     * {@code {user.home}/.jemm/tagmap.json} when nothing is stored.
     *
     * @return the tag-map file path, never null
     */
    public String getTagMapPath() {
        try {
            String def = System.getProperty("user.home") + java.io.File.separator + ".jemm"
                    + java.io.File.separator + "tagmap.json";
            return prefs.get(KEY_TAG_MAP_PATH, def);
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to read tag map path", ex);
            return System.getProperty("user.home") + java.io.File.separator + ".jemm"
                    + java.io.File.separator + "tagmap.json";
        }
    }

    /**
     * Stores the file location of the active Tag-Team tag map.
     *
     * @param path the tag-map file path
     */
    public void setTagMapPath(String path) {
        try {
            if (path != null && !path.trim().isEmpty()) {
                prefs.put(KEY_TAG_MAP_PATH, path.trim());
                prefs.flush();
            }
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to save tag map path", ex);
        }
    }

    /**
     * Returns whether Tag-Team mode should include the given media class.
     *
     * @param kind one of "videos", "images" or "other"
     * @return true when that media class should be walked
     */
    public boolean isTagTeamIncludes(String kind) {
        try {
            switch (kind) {
                case "videos":
                    return prefs.getBoolean(KEY_TAG_TEAM_INCLUDE_VIDEOS, true);
                case "images":
                    return prefs.getBoolean(KEY_TAG_TEAM_INCLUDE_IMAGES, false);
                case "other":
                    return prefs.getBoolean(KEY_TAG_TEAM_INCLUDE_OTHER, false);
                default:
                    return false;
            }
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to read tag team filter", ex);
            return "videos".equals(kind);
        }
    }

    /**
     * Stores whether Tag-Team mode should include the given media class.
     *
     * @param kind    one of "videos", "images" or "other"
     * @param enabled true to include that media class
     */
    public void setTagTeamIncludes(String kind, boolean enabled) {
        try {
            switch (kind) {
                case "videos":
                    prefs.putBoolean(KEY_TAG_TEAM_INCLUDE_VIDEOS, enabled);
                    break;
                case "images":
                    prefs.putBoolean(KEY_TAG_TEAM_INCLUDE_IMAGES, enabled);
                    break;
                case "other":
                    prefs.putBoolean(KEY_TAG_TEAM_INCLUDE_OTHER, enabled);
                    break;
                default:
                    return;
            }
            prefs.flush();
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to save tag team filter", ex);
        }
    }

    /**
     * Returns the last chosen "cascade folder base into nested subfolders" preference.
     *
     * @return true when folder stops should cascade into nested subfolders
     */
    public boolean isTagTeamCascadeSubfolders() {
        try {
            return prefs.getBoolean(KEY_TAG_TEAM_CASCADE_SUBFOLDERS, false);
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to read cascade flag", ex);
            return false;
        }
    }

    /**
     * Stores the "cascade folder base into nested subfolders" preference.
     *
     * @param enabled true to cascade folder base values recursively
     */
    public void setTagTeamCascadeSubfolders(boolean enabled) {
        try {
            prefs.putBoolean(KEY_TAG_TEAM_CASCADE_SUBFOLDERS, enabled);
            prefs.flush();
        } catch (Exception ex) {
            Logger.getLogger(JemmSettingsStore.class.getName()).log(Level.WARNING, "Failed to save cascade flag", ex);
        }
    }
}
