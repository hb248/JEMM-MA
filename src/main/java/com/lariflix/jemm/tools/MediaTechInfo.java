package com.lariflix.jemm.tools;

import com.lariflix.jemm.dtos.JellyfinMediaSource;
import com.lariflix.jemm.dtos.JellyfinMediaStream;
import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Extracted technical media facts used by auto-tag rules.
 */
public class MediaTechInfo {

    private int width;
    private int height;
    private double frameRate;
    private long bitRate;
    private long fileSize;
    private boolean video;
    private boolean image;
    private boolean valid;

    public static MediaTechInfo fromMetadata(JellyfinItemMetadata metadata) {
        MediaTechInfo info = new MediaTechInfo();
        if (metadata == null) {
            return info;
        }

        String mediaType = safe(metadata.getMediaType()).toLowerCase(Locale.ROOT);
        String type = safe(metadata.getType()).toLowerCase(Locale.ROOT);
        info.image = mediaType.contains("photo") || mediaType.contains("image")
                || type.contains("photo") || type.contains("image");
        info.video = !info.image && (mediaType.contains("video")
                || type.contains("movie")
                || type.contains("episode")
                || type.contains("video")
                || type.contains("trailer"));

        JellyfinMediaStream stream = findPrimaryStream(metadata.getMediaSources(), info.image);
        if (stream != null) {
            info.width = stream.getWidth() == null ? 0 : stream.getWidth();
            info.height = stream.getHeight() == null ? 0 : stream.getHeight();
            if (stream.getAverageFrameRate() != null) {
                info.frameRate = stream.getAverageFrameRate();
            } else if (stream.getRealFrameRate() != null) {
                info.frameRate = stream.getRealFrameRate();
            }
            if (stream.getBitRate() != null) {
                info.bitRate = stream.getBitRate();
            }
            info.valid = info.width > 0 && info.height > 0;
        }

        if (metadata.getMediaSources() != null) {
            for (JellyfinMediaSource source : metadata.getMediaSources()) {
                if (source == null) {
                    continue;
                }
                if (info.bitRate <= 0 && source.getBitrate() != null) {
                    info.bitRate = source.getBitrate();
                }
                if (source.getSize() != null && source.getSize() > info.fileSize) {
                    info.fileSize = source.getSize();
                }
            }
        }

        if (!info.valid && metadata.getAspectRatio() != null && !metadata.getAspectRatio().isBlank()) {
            // Keep invalid dimensions but allow orientation fallback via aspect ratio string in rules.
            info.valid = false;
        }

        if (!info.video && !info.image) {
            // Fallback: treat as video when we have frame rate / typical movie containers.
            info.video = info.frameRate > 0 || info.bitRate > 0;
        }

        return info;
    }

    private static JellyfinMediaStream findPrimaryStream(ArrayList<JellyfinMediaSource> sources, boolean preferImage) {
        if (sources == null) {
            return null;
        }
        JellyfinMediaStream typedFallback = null;
        JellyfinMediaStream anyDimensioned = null;
        for (JellyfinMediaSource source : sources) {
            if (source == null || source.getMediaStreams() == null) {
                continue;
            }
            for (JellyfinMediaStream stream : source.getMediaStreams()) {
                if (stream == null) {
                    continue;
                }
                boolean dimensioned = stream.getWidth() != null && stream.getHeight() != null
                        && stream.getWidth() > 0 && stream.getHeight() > 0;
                if (dimensioned && anyDimensioned == null) {
                    anyDimensioned = stream;
                }
                if (stream.getType() == null) {
                    continue;
                }
                String streamType = stream.getType().toLowerCase(Locale.ROOT);
                boolean match = preferImage ? streamType.contains("video") || streamType.contains("embeddedimage")
                        : streamType.equals("video");
                if (!match && preferImage && dimensioned) {
                    match = true;
                }
                if (!match) {
                    continue;
                }
                if (stream.isIsDefault() && dimensioned) {
                    return stream;
                }
                if (typedFallback == null && dimensioned) {
                    typedFallback = stream;
                }
            }
        }
        // Prefer a typed (video/image) stream; otherwise any stream that carries width/height.
        return typedFallback != null ? typedFallback : anyDimensioned;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public double getFrameRate() {
        return frameRate;
    }

    public long getBitRate() {
        return bitRate;
    }

    public long getFileSize() {
        return fileSize;
    }

    public boolean isVideo() {
        return video;
    }

    public boolean isImage() {
        return image;
    }

    public boolean isValid() {
        return valid;
    }

    public double megapixels() {
        if (width <= 0 || height <= 0) {
            return 0;
        }
        return (width * (double) height) / 1_000_000d;
    }

    public int longerEdge() {
        return Math.max(width, height);
    }
}
