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
    private boolean hasAudio;
    private boolean audioKnown;

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
                // Whenever a source exposes its stream list we can determine audio presence.
                if (source.getMediaStreams() != null) {
                    info.audioKnown = true;
                    for (JellyfinMediaStream s : source.getMediaStreams()) {
                        if (s != null && s.getType() != null
                                && s.getType().toLowerCase(Locale.ROOT).contains("audio")) {
                            info.hasAudio = true;
                        }
                    }
                }
            }
        }

        // Fallback to the item-level Width/Height (Jellyfin exposes these for videos and photos)
        // when no MediaStream provided usable dimensions.
        if ((info.width <= 0 || info.height <= 0)) {
            if (metadata.getWidth() != null && metadata.getWidth() > 0) {
                info.width = metadata.getWidth();
            }
            if (metadata.getHeight() != null && metadata.getHeight() > 0) {
                info.height = metadata.getHeight();
            }
        }
        info.valid = info.width > 0 && info.height > 0;

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

    /**
     * @return true when an audio stream was detected
     */
    public boolean isHasAudio() {
        return hasAudio;
    }

    /**
     * @return true when audio presence could actually be determined (stream info available)
     */
    public boolean isAudioKnown() {
        return audioKnown;
    }

    /**
     * Indicates whether the API-derived facts are incomplete and ffprobe should
     * be consulted to fill the gaps.
     *
     * @return true when dimensions are missing, or a video lacks fps/bitrate,
     *         or an image lacks a file size
     */
    public boolean needsProbe() {
        if (width <= 0 || height <= 0) {
            return true;
        }
        if (video && (frameRate <= 0 || bitRate <= 0)) {
            return true;
        }
        if (image && fileSize <= 0) {
            return true;
        }
        return false;
    }

    /**
     * Fills only the currently-missing fields from an ffprobe result, then
     * recomputes validity. Existing (API-provided) values are never overwritten.
     *
     * @param probe the ffprobe result, may be null
     */
    public void merge(FfprobeResult probe) {
        if (probe == null) {
            return;
        }
        if (width <= 0 && probe.getWidth() > 0) {
            width = probe.getWidth();
        }
        if (height <= 0 && probe.getHeight() > 0) {
            height = probe.getHeight();
        }
        if (frameRate <= 0 && probe.getFrameRate() > 0) {
            frameRate = probe.getFrameRate();
        }
        if (bitRate <= 0 && probe.getBitRate() > 0) {
            bitRate = probe.getBitRate();
        }
        if (fileSize <= 0 && probe.getFileSize() > 0) {
            fileSize = probe.getFileSize();
        }
        if (!audioKnown && probe.isAudioKnown()) {
            hasAudio = probe.isHasAudio();
            audioKnown = true;
        }
        valid = width > 0 && height > 0;
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
