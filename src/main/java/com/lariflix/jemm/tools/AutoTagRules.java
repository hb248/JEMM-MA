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

    public List<String> compute(MediaTechInfo info) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (info == null) {
            return new ArrayList<>(tags);
        }

        String orientation = orientationTag(info);
        if (orientation != null) {
            tags.add(orientation);
        }

        String resolution = resolutionTag(info);
        if (resolution != null) {
            tags.add(resolution);
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

    public String orientationTag(MediaTechInfo info) {
        // Orientation is derived only from real pixel dimensions. When they are missing,
        // no orientation is guessed (ffprobe is the single fallback that fills dimensions upstream).
        if (info.getWidth() <= 0 || info.getHeight() <= 0) {
            return null;
        }
        return orientationFromRatio(info.getWidth() / (double) info.getHeight());
    }

    /**
     * Derives an orientation tag from a poster/aspect-ratio hint (e.g. Jellyfin's
     * {@code AspectRatio} string or {@code PrimaryImageAspectRatio}). This is only used
     * as a degraded fallback when real pixel dimensions are unavailable and ffprobe
     * could not supply them.
     *
     * @param aspectHint a ratio string such as "16:9", "1.777" or "1080x1920"
     * @return an orientation tag, or null when the hint is not parseable
     */
    public String orientationFromAspect(String aspectHint) {
        return orientationFromRatio(parseAspectRatio(aspectHint));
    }

    private String orientationFromRatio(double ratio) {
        if (ratio <= 0) {
            return null;
        }
        if (Math.abs(ratio - 1.0d) <= SQUARE_TOLERANCE) {
            return ManagedAutoTags.SQUARE;
        }
        return ratio < 1.0d ? ManagedAutoTags.VERTICAL : ManagedAutoTags.HORIZONTAL;
    }

    /**
     * Parses an aspect-ratio hint into a width/height ratio.
     *
     * @param aspectRatio a ratio string such as "16:9", "1.777" or "1080x1920"
     * @return the ratio, or 0 when not parseable
     */
    public double parseAspectRatio(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isBlank()) {
            return 0;
        }
        String normalized = aspectRatio.trim().toLowerCase(Locale.ROOT).replace(':', '/').replace('x', '/');
        String[] parts = normalized.split("/");
        if (parts.length == 1) {
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
        if (info.getWidth() <= 0 || info.getHeight() <= 0) {
            return null;
        }
        // Classify by the shorter edge (the "vertical" resolution) so the ladder is
        // orientation-independent and identical for videos and images.
        int measure = Math.min(info.getWidth(), info.getHeight());
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
        if (measure < 2880) {
            return ManagedAutoTags.FOUR_K;
        }
        if (measure < 3840) {
            return ManagedAutoTags.SIX_K;
        }
        if (measure < 5760) {
            return ManagedAutoTags.EIGHT_K;
        }
        return ManagedAutoTags.ULTRA_RES;
    }

    /** Lower/upper bounds of the "standart fps" range, used to normalize bitrate. */
    public static final double STANDARD_FPS_MIN = 24d;
    public static final double STANDARD_FPS_MAX = 30d;
    /** Photo file sizes are multiplied by this factor before scoring. */
    public static final double IMAGE_SIZE_FACTOR = 4d;

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
            mbps = normalizeVideoMbpsForFps(mbps, info.getFrameRate());
            score = mbps / mp;
        } else if (info.isImage()) {
            if (info.getFileSize() <= 0) {
                return null;
            }
            double sizeMb = info.getFileSize() / (1024d * 1024d);
            score = (sizeMb * IMAGE_SIZE_FACTOR) / mp;
        } else {
            return null;
        }
        return ManagedAutoTags.qrLevel(scoreToLevel(score));
    }

    /**
     * Normalizes a video's Mbps to the "standart fps" range so the quality rating is
     * comparable across frame rates. The per-frame bitrate ({@code Mbps / fps}) is the
     * real quality signal, so low/high fps videos are rescaled to the nearest standard
     * boundary (24 or 30 fps).
     *
     * @param mbps the raw bitrate in Mbps
     * @param fps  the video frame rate (0 when unknown)
     * @return the fps-normalized Mbps
     */
    public double normalizeVideoMbpsForFps(double mbps, double fps) {
        if (fps <= 0) {
            return mbps;
        }
        if (fps < STANDARD_FPS_MIN) {
            return mbps * (STANDARD_FPS_MIN / fps);
        }
        if (fps > STANDARD_FPS_MAX) {
            return mbps * (STANDARD_FPS_MAX / fps);
        }
        return mbps;
    }

    /**
     * Maps a quality score to a QR level linearly, using normal rounding. Scores too
     * low for QR1 collapse to QR0.
     *
     * @param score the quality score
     * @return the QR level (>= 0)
     */
    public int scoreToLevel(double score) {
        return (int) Math.max(0L, Math.round(score));
    }
}
