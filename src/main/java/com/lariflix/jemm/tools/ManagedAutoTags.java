package com.lariflix.jemm.tools;

import com.lariflix.jemm.utils.MetadataListMerge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Vocabulary and sync helpers for managed auto-tags.
 */
public final class ManagedAutoTags {

    public static final String VERTICAL = "vertical";
    public static final String HORIZONTAL = "horizontal";
    public static final String SQUARE = "square";
    public static final String LOW_FPS = "low fps";
    public static final String STANDART_FPS = "standart fps";
    public static final String HIGH_FPS = "high fps";
    public static final String SD = "SD";
    public static final String HD = "HD";
    public static final String FULL_HD = "FULL HD";
    public static final String TWO_K = "2K";
    public static final String FOUR_K = "4K";
    public static final String SIX_K = "6K";
    public static final String EIGHT_K = "8K";
    public static final String ULTRA_RES = "ULTRA RES";
    public static final String NO_AUDIO = "No Audio";

    /** Managed auto-tag categories, each independently selectable in the UI. */
    public enum Category {
        ORIENTATION, FPS, RESOLUTION, QUALITY, AUDIO
    }

    private static final Set<String> ORIENTATION = Set.of(VERTICAL, HORIZONTAL, SQUARE);
    private static final Set<String> FPS = Set.of(LOW_FPS, STANDART_FPS, HIGH_FPS);
    private static final Set<String> RESOLUTION = Set.of(SD, HD, FULL_HD, TWO_K, FOUR_K, SIX_K, EIGHT_K, ULTRA_RES);

    private ManagedAutoTags() {
    }

    public static String qrLevel(int level) {
        return "QR" + Math.max(0, level);
    }

    /**
     * Determines which managed category a tag belongs to.
     *
     * @param tag the tag to classify
     * @return the category, or null when the tag is not a managed auto-tag
     */
    public static Category category(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String normalized = tag.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (ORIENTATION.contains(lower)) {
            return Category.ORIENTATION;
        }
        if (FPS.contains(lower)) {
            return Category.FPS;
        }
        for (String res : RESOLUTION) {
            if (res.equalsIgnoreCase(normalized)) {
                return Category.RESOLUTION;
            }
        }
        if (NO_AUDIO.equalsIgnoreCase(normalized)) {
            return Category.AUDIO;
        }
        if (lower.matches("qr\\d+")) {
            return Category.QUALITY;
        }
        return null;
    }

    public static boolean isManaged(String tag) {
        return category(tag) != null;
    }

    public static ArrayList<String> sync(ArrayList<String> existing, List<String> computed) {
        return sync(existing, computed, EnumSet.allOf(Category.class));
    }

    /**
     * Syncs managed auto-tags but only for the categories in {@code scope}. Tags of
     * categories outside the scope (and all non-managed tags) are preserved untouched,
     * so users can enable/disable categories without losing others.
     *
     * @param existing the item's current tags
     * @param computed the freshly computed tags (already limited to enabled categories)
     * @param scope    the categories that this run is authoritative for
     * @return the new tag list, or null when nothing changed
     */
    public static ArrayList<String> sync(ArrayList<String> existing, List<String> computed, EnumSet<Category> scope) {
        EnumSet<Category> effectiveScope = scope == null ? EnumSet.noneOf(Category.class) : scope;
        ArrayList<String> current = existing == null ? new ArrayList<>() : new ArrayList<>(existing);

        // Keep everything that is not managed, or managed but outside the current scope.
        ArrayList<String> kept = new ArrayList<>();
        for (String tag : current) {
            Category cat = category(tag);
            if (cat == null || !effectiveScope.contains(cat)) {
                kept.add(tag);
            }
        }

        ArrayList<String> desiredManaged = new ArrayList<>();
        if (computed != null) {
            for (String tag : computed) {
                Category cat = category(tag);
                if (tag != null && !tag.isBlank() && cat != null && effectiveScope.contains(cat)) {
                    desiredManaged.add(tag.trim());
                }
            }
        }
        desiredManaged = MetadataListMerge.dedupeTags(desiredManaged);

        ArrayList<String> existingInScope = new ArrayList<>();
        for (String tag : current) {
            Category cat = category(tag);
            if (cat != null && effectiveScope.contains(cat)) {
                existingInScope.add(tag);
            }
        }
        existingInScope = MetadataListMerge.dedupeTags(existingInScope);

        if (sameIgnoreCase(existingInScope, desiredManaged)) {
            return null; // unchanged
        }

        ArrayList<String> result = new ArrayList<>(kept);
        result.addAll(desiredManaged);
        return MetadataListMerge.dedupeTags(result);
    }

    private static boolean sameIgnoreCase(List<String> left, List<String> right) {
        if (left.size() != right.size()) {
            return false;
        }
        Set<String> leftKeys = new LinkedHashSet<>();
        for (String value : left) {
            leftKeys.add(value.toLowerCase(Locale.ROOT));
        }
        Set<String> rightKeys = new LinkedHashSet<>();
        for (String value : right) {
            rightKeys.add(value.toLowerCase(Locale.ROOT));
        }
        return leftKeys.equals(rightKeys);
    }

    public static Set<String> allKnownExact() {
        Set<String> all = new LinkedHashSet<>();
        all.addAll(ORIENTATION);
        all.addAll(FPS);
        all.addAll(RESOLUTION);
        all.add(NO_AUDIO);
        return Collections.unmodifiableSet(all);
    }
}
