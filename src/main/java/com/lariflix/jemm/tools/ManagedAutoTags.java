package com.lariflix.jemm.tools;

import com.lariflix.jemm.utils.MetadataListMerge;
import java.util.ArrayList;
import java.util.Collections;
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
    public static final String ULTRA_RES = "ULTRA RES";

    private static final Set<String> ORIENTATION = Set.of(VERTICAL, HORIZONTAL, SQUARE);
    private static final Set<String> FPS = Set.of(LOW_FPS, STANDART_FPS, HIGH_FPS);
    private static final Set<String> RESOLUTION = Set.of(SD, HD, FULL_HD, TWO_K, FOUR_K, ULTRA_RES);

    private ManagedAutoTags() {
    }

    public static String qrLevel(int level) {
        return "QR" + Math.max(1, level);
    }

    public static boolean isManaged(String tag) {
        if (tag == null || tag.isBlank()) {
            return false;
        }
        String normalized = tag.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (ORIENTATION.contains(lower)) {
            return true;
        }
        if (FPS.contains(lower)) {
            return true;
        }
        for (String res : RESOLUTION) {
            if (res.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return lower.matches("qr\\d+");
    }

    public static ArrayList<String> sync(ArrayList<String> existing, List<String> computed) {
        ArrayList<String> current = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        ArrayList<String> withoutManaged = new ArrayList<>();
        for (String tag : current) {
            if (!isManaged(tag)) {
                withoutManaged.add(tag);
            }
        }
        ArrayList<String> desiredManaged = new ArrayList<>();
        if (computed != null) {
            for (String tag : computed) {
                if (tag != null && !tag.isBlank() && isManaged(tag)) {
                    desiredManaged.add(tag.trim());
                }
            }
        }
        desiredManaged = MetadataListMerge.dedupeTags(desiredManaged);

        ArrayList<String> existingManaged = new ArrayList<>();
        for (String tag : current) {
            if (isManaged(tag)) {
                existingManaged.add(tag);
            }
        }
        existingManaged = MetadataListMerge.dedupeTags(existingManaged);

        if (sameIgnoreCase(existingManaged, desiredManaged)) {
            return null; // unchanged
        }

        ArrayList<String> result = new ArrayList<>(withoutManaged);
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
        return Collections.unmodifiableSet(all);
    }
}
