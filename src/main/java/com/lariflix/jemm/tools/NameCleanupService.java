package com.lariflix.jemm.tools;

import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import java.util.regex.Pattern;

/**
 * Reverts the episode-style naming again: removes the {@code " - EP##"} suffix (or resets the name
 * from the file path) and clears the Original Title / Sort Name so items fall back to a clean name.
 */
public class NameCleanupService {

    /** Matches a trailing " - EP<number>" suffix, case-insensitive. */
    private static final Pattern EP_SUFFIX = Pattern.compile("(?i)\\s*-\\s*EP\\d+\\s*$");

    /**
     * How the item Name should be treated.
     */
    public enum NameMode {
        /** Keep the current Name untouched. */
        KEEP,
        /** Strip a trailing " - EP##" suffix. */
        REMOVE_EP_SUFFIX,
        /** Replace the Name with the file name derived from the item path. */
        RESET_FROM_PATH
    }

    /**
     * Runtime options for the cleanup.
     */
    public static class Config {
        public NameMode nameMode = NameMode.REMOVE_EP_SUFFIX;
        /** Clear Original Title and Sort Name (the latter is coupled to Original Title on save). */
        public boolean clearOriginalAndSort = true;
    }

    /**
     * Mutates the metadata in place. Returns {@code true} if anything changed.
     */
    public boolean apply(JellyfinItemMetadata metadata, Config config) {
        if (metadata == null || config == null) {
            return false;
        }
        boolean changed = false;

        switch (config.nameMode) {
            case RESET_FROM_PATH: {
                String fileName = fileNameFromPath(metadata.getPath());
                if (fileName != null && !fileName.isBlank() && !fileName.equals(metadata.getName())) {
                    metadata.setName(fileName);
                    changed = true;
                }
                break;
            }
            case REMOVE_EP_SUFFIX: {
                String name = metadata.getName();
                if (name != null) {
                    String stripped = EP_SUFFIX.matcher(name).replaceAll("");
                    if (!stripped.equals(name)) {
                        metadata.setName(stripped);
                        changed = true;
                    }
                }
                break;
            }
            case KEEP:
            default:
                break;
        }

        if (config.clearOriginalAndSort) {
            if (isSet(metadata.getOriginalTitle()) || isSet(metadata.getForcedSortName()) || isSet(metadata.getSortName())) {
                metadata.setOriginalTitle(null);
                metadata.setForcedSortName(null);
                metadata.setSortName(null);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Extracts the file name (without extension) from a Jellyfin item path. Handles both Windows
     * and Unix separators.
     */
    static String fileNameFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.trim();
    }
}
