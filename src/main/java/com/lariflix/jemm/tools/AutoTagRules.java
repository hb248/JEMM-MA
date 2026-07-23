package com.lariflix.jemm.tools;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Computes managed auto-tags from technical media info.
 */
public class AutoTagRules {

    public static final double SQUARE_TOLERANCE = 0.08d;
    public static final double ULTRA_RES_MEGAPIXELS = 8.0d;

    public List<String> compute(MediaTechInfo info, String aspectRatioFallback) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (info == null) {
            return new ArrayList<>(tags);
        }

        String orientation = orientationTag(info, aspectRatioFallback);
        if (orientation != null) {
            tags.add(orientation);
        }

        String resolution = resolutionTag(info);
        if (resolution != null) {
            tags.add(resolution);
        }

        if (info.isImage() && info.megapixels() >= ULTRA_RES_MEGAPIXELS) {
            tags.add(ManagedAutoTags.ULTRA_RES);
        }

        if (info.isVideo() && info.getFrameRate() > 0) {
            tags.add(fpsTag(info.getFrameRate()));
        }

        String qr = qualityRatingTag(info);
        if (qr != null) {
            tags.add(qr);
        }

        return new ArrayList<>(tags);
    }

    public String orientationTag(MediaTechInfo info, String aspectRatioFallback) {
        double ratio = 0;
        if (info.getWidth() > 0 && info.getHeight() > 0) {
            ratio = info.getWidth() / (double) info.getHeight();
        } else {
            ratio = parseAspectRatio(aspectRatioFallback);
        }
        if (ratio <= 0) {
            return null;
        }
        if (Math.abs(ratio - 1.0d) <= SQUARE_TOLERANCE) {
            return ManagedAutoTags.SQUARE;
        }
        return ratio < 1.0d ? ManagedAutoTags.VERTICAL : ManagedAutoTags.HORIZONTAL;
    }

    public String fpsTag(double fps) {
        if (fps < 24d) {
            return ManagedAutoTags.LOW_FPS;
        }
        if (fps <= 30d) {
            return ManagedAutoTags.STANDART_FPS;
        }
        return ManagedAutoTags.HIGH_FPS;
    }

    public String resolutionTag(MediaTechInfo info) {
        int edge = info.longerEdge();
        if (edge <= 0) {
            return null;
        }
        // Use height for landscape; longer edge covers portrait.
        int measure = info.getHeight() > 0 ? Math.max(info.getHeight(), info.getWidth() < info.getHeight() ? info.getWidth() : info.getHeight()) : edge;
        if (info.getWidth() > 0 && info.getHeight() > 0) {
            measure = info.getWidth() >= info.getHeight() ? info.getHeight() : info.getWidth();
        }
        if (measure < 720) {
            return ManagedAutoTags.SD;
        }
        if (measure < 1080) {
            return ManagedAutoTags.HD;
        }
        if (measure < 1440) {
            return ManagedAutoTags.FULL_HD;
        }
        if (measure < 2160) {
            return ManagedAutoTags.TWO_K;
        }
        return ManagedAutoTags.FOUR_K;
    }

    public String qualityRatingTag(MediaTechInfo info) {
        double mp = info.megapixels();
        if (mp <= 0) {
            return null;
        }
        double score;
        if (info.isVideo()) {
            if (info.getBitRate() <= 0) {
                return null;
            }
            double mbps = info.getBitRate() / 1_000_000d;
            score = mbps / mp;
        } else if (info.isImage()) {
            if (info.getFileSize() <= 0) {
                return null;
            }
            double sizeMb = info.getFileSize() / (1024d * 1024d);
            score = sizeMb / mp;
        } else {
            return null;
        }
        return ManagedAutoTags.qrLevel(scoreToLevel(score));
    }

    public int scoreToLevel(double score) {
        if (score < 2d) {
            return 1;
        }
        if (score < 4d) {
            return 2;
        }
        if (score < 7d) {
            return 3;
        }
        if (score < 12d) {
            return 4;
        }
        return 5 + Math.max(0, (int) Math.floor((score - 12d) / 5d));
    }

    public double parseAspectRatio(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isBlank()) {
            return 0;
        }
        String normalized = aspectRatio.trim().toLowerCase(Locale.ROOT).replace(':', '/').replace('x', '/');
        String[] parts = normalized.split("/");
        if (parts.length == 1) {
            // A single decimal value (e.g. Jellyfin PrimaryImageAspectRatio) is already width/height.
            try {
                double ratio = Double.parseDouble(parts[0].trim());
                return ratio > 0 ? ratio : 0;
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
        if (parts.length != 2) {
            return 0;
        }
        try {
            double w = Double.parseDouble(parts[0].trim());
            double h = Double.parseDouble(parts[1].trim());
            if (h == 0) {
                return 0;
            }
            return w / h;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
