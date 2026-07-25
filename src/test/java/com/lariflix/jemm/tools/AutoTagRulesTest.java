package com.lariflix.jemm.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;

public class AutoTagRulesTest {

    private final AutoTagRules rules = new AutoTagRules();

    @Test
    public void orientationUsesSquareTolerance() {
        MediaTechInfo info = tech(1000, 1000, 30, 8_000_000, true, false);
        assertEquals(ManagedAutoTags.SQUARE, rules.orientationTag(info));

        MediaTechInfo nearSquare = tech(1050, 1000, 30, 8_000_000, true, false);
        assertEquals(ManagedAutoTags.SQUARE, rules.orientationTag(nearSquare));

        MediaTechInfo vertical = tech(1080, 1920, 30, 8_000_000, true, false);
        assertEquals(ManagedAutoTags.VERTICAL, rules.orientationTag(vertical));
    }

    @Test
    public void fpsBuckets() {
        assertEquals(ManagedAutoTags.LOW_FPS, rules.fpsTag(23.9));
        assertEquals(ManagedAutoTags.STANDART_FPS, rules.fpsTag(24));
        assertEquals(ManagedAutoTags.STANDART_FPS, rules.fpsTag(30));
        assertEquals(ManagedAutoTags.HIGH_FPS, rules.fpsTag(60));
    }

    @Test
    public void resolutionBuckets() {
        assertEquals(ManagedAutoTags.SD, rules.resolutionTag(tech(640, 480, 30, 1_000_000, true, false)));
        assertEquals(ManagedAutoTags.HD, rules.resolutionTag(tech(1280, 720, 30, 1_000_000, true, false)));
        assertEquals(ManagedAutoTags.FULL_HD, rules.resolutionTag(tech(1920, 1080, 30, 1_000_000, true, false)));
        assertEquals(ManagedAutoTags.TWO_K, rules.resolutionTag(tech(2560, 1440, 30, 1_000_000, true, false)));
        assertEquals(ManagedAutoTags.FOUR_K, rules.resolutionTag(tech(3840, 2160, 30, 1_000_000, true, false)));
        assertEquals(ManagedAutoTags.SIX_K, rules.resolutionTag(tech(6144, 3456, 30, 1_000_000, true, false)));
        assertEquals(ManagedAutoTags.EIGHT_K, rules.resolutionTag(tech(7680, 4320, 30, 1_000_000, true, false)));
        assertEquals(ManagedAutoTags.ULTRA_RES, rules.resolutionTag(tech(15360, 8640, 30, 1_000_000, true, false)));
    }

    @Test
    public void ultraResReplacesTopTierForImages() {
        // ULTRA RES is part of the resolution ladder (not an additional tag) for images too.
        List<String> tags = rules.compute(tech(15360, 8640, 0, 0, false, true));
        assertTrue(tags.contains(ManagedAutoTags.ULTRA_RES));
        assertFalse(tags.contains(ManagedAutoTags.FOUR_K));
        assertFalse(tags.contains(ManagedAutoTags.EIGHT_K));
    }

    @Test
    public void orientationFromAspectHint() {
        assertEquals(ManagedAutoTags.HORIZONTAL, rules.orientationFromAspect("16:9"));
        assertEquals(ManagedAutoTags.VERTICAL, rules.orientationFromAspect("9:16"));
        assertEquals(ManagedAutoTags.SQUARE, rules.orientationFromAspect("1:1"));
        assertEquals(ManagedAutoTags.SQUARE, rules.orientationFromAspect("1.0"));
        assertEquals(ManagedAutoTags.VERTICAL, rules.orientationFromAspect("1080x1920"));
        assertNull(rules.orientationFromAspect(""));
        assertNull(rules.orientationFromAspect(null));
    }

    @Test
    public void scoreToLevelIsLinearWithRoundingAndQr0() {
        assertEquals(4, rules.scoreToLevel(3.86));
        assertEquals(5, rules.scoreToLevel(4.82));
        assertEquals(0, rules.scoreToLevel(0.48));
        assertEquals(1, rules.scoreToLevel(0.5));
        assertEquals(15, rules.scoreToLevel(15.0));
        assertEquals(0, rules.scoreToLevel(0));
    }

    @Test
    public void normalizeVideoMbpsForFps() {
        assertEquals(8.0, rules.normalizeVideoMbpsForFps(8.0, 28), 0.0001);   // standard, unchanged
        assertEquals(8.0, rules.normalizeVideoMbpsForFps(8.0, 24), 0.0001);   // lower bound, unchanged
        assertEquals(8.0, rules.normalizeVideoMbpsForFps(8.0, 30), 0.0001);   // upper bound, unchanged
        assertEquals(10.0, rules.normalizeVideoMbpsForFps(5.0, 12), 0.0001);  // low fps -> *24/12
        assertEquals(10.0, rules.normalizeVideoMbpsForFps(20.0, 60), 0.0001); // high fps -> *30/60
        assertEquals(8.0, rules.normalizeVideoMbpsForFps(8.0, 0), 0.0001);    // unknown fps, unchanged
    }

    @Test
    public void qualityRatingVideoUsesFpsNormalization() {
        // 1080p, 30fps, 8 Mbps -> 3.86 -> QR4
        assertEquals("QR4", rules.qualityRatingTag(tech(1920, 1080, 30, 8_000_000, true, false)));
        // 1080p, 30fps, 1 Mbps -> 0.48 -> QR0
        assertEquals("QR0", rules.qualityRatingTag(tech(1920, 1080, 30, 1_000_000, true, false)));
        // 1080p, 12fps (low), 5 Mbps -> normalized 10 Mbps -> 4.82 -> QR5
        assertEquals("QR5", rules.qualityRatingTag(tech(1920, 1080, 12, 5_000_000, true, false)));
        // 1080p, 60fps (high), 20 Mbps -> normalized 10 Mbps -> 4.82 -> QR5
        assertEquals("QR5", rules.qualityRatingTag(tech(1920, 1080, 60, 20_000_000, true, false)));
    }

    @Test
    public void qualityRatingImageMultipliesSizeByFour() {
        // 24 MP, 12 MB -> (12*4)/24 = 2.0 -> QR2
        assertEquals("QR2", rules.qualityRatingTag(imageTech(6000, 4000, 12L * 1024 * 1024)));
        // 24 MP, 2 MB -> (2*4)/24 = 0.33 -> QR0
        assertEquals("QR0", rules.qualityRatingTag(imageTech(6000, 4000, 2L * 1024 * 1024)));
    }

    @Test
    public void managedSyncKeepsManualTags() {
        ArrayList<String> existing = new ArrayList<>();
        existing.add("manual");
        existing.add("HD");

        List<String> computed = List.of("vertical", "FULL HD", "standart fps", "QR3");
        ArrayList<String> synced = ManagedAutoTags.sync(existing, computed);

        assertNotNull(synced);
        assertTrue(synced.stream().anyMatch(t -> t.equalsIgnoreCase("manual")));
        assertTrue(synced.stream().anyMatch(t -> t.equalsIgnoreCase("vertical")));
        assertTrue(synced.stream().noneMatch(t -> t.equals("HD")));
        assertTrue(synced.stream().anyMatch(t -> t.equals("FULL HD")));
    }

    @Test
    public void managedSyncReturnsNullWhenUnchanged() {
        ArrayList<String> existing = new ArrayList<>();
        existing.add("manual");
        existing.add("vertical");
        existing.add("FULL HD");

        List<String> computed = List.of("vertical", "FULL HD");
        assertNull(ManagedAutoTags.sync(existing, computed));
    }

    private MediaTechInfo imageTech(int w, int h, long sizeBytes) {
        com.lariflix.jemm.dtos.JellyfinItemMetadata metadata = new com.lariflix.jemm.dtos.JellyfinItemMetadata();
        metadata.setMediaType("Photo");
        metadata.setType("Photo");
        com.lariflix.jemm.dtos.JellyfinMediaSource source = new com.lariflix.jemm.dtos.JellyfinMediaSource();
        source.setSize(sizeBytes);
        com.lariflix.jemm.dtos.JellyfinMediaStream stream = new com.lariflix.jemm.dtos.JellyfinMediaStream();
        stream.setType("Video");
        stream.setWidth(w);
        stream.setHeight(h);
        stream.setIsDefault(true);
        java.util.ArrayList<com.lariflix.jemm.dtos.JellyfinMediaStream> streams = new java.util.ArrayList<>();
        streams.add(stream);
        source.setMediaStreams(streams);
        java.util.ArrayList<com.lariflix.jemm.dtos.JellyfinMediaSource> sources = new java.util.ArrayList<>();
        sources.add(source);
        metadata.setMediaSources(sources);
        return MediaTechInfo.fromMetadata(metadata);
    }

    private MediaTechInfo tech(int w, int h, double fps, long bitrate, boolean video, boolean image) {
        // Build via metadata-less fake by using reflection-free setter path through a tiny subclass is overkill;
        // use MediaTechInfo fields via fromMetadata is hard without DTO. Instead create and patch via package methods.
        JellyfinMediaStreamStub stub = new JellyfinMediaStreamStub(w, h, fps, bitrate, video, image);
        return stub.info;
    }

    /** Simple holder to construct MediaTechInfo without public setters. */
    private static class JellyfinMediaStreamStub {
        final MediaTechInfo info;

        JellyfinMediaStreamStub(int w, int h, double fps, long bitrate, boolean video, boolean image) {
            com.lariflix.jemm.dtos.JellyfinItemMetadata metadata = new com.lariflix.jemm.dtos.JellyfinItemMetadata();
            metadata.setMediaType(image ? "Photo" : "Video");
            metadata.setType(image ? "Photo" : "Movie");
            com.lariflix.jemm.dtos.JellyfinMediaSource source = new com.lariflix.jemm.dtos.JellyfinMediaSource();
            source.setBitrate(bitrate);
            source.setSize(5_000_000L);
            com.lariflix.jemm.dtos.JellyfinMediaStream stream = new com.lariflix.jemm.dtos.JellyfinMediaStream();
            stream.setType("Video");
            stream.setWidth(w);
            stream.setHeight(h);
            stream.setAverageFrameRate(fps);
            stream.setBitRate(bitrate);
            stream.setIsDefault(true);
            java.util.ArrayList<com.lariflix.jemm.dtos.JellyfinMediaStream> streams = new java.util.ArrayList<>();
            streams.add(stream);
            source.setMediaStreams(streams);
            java.util.ArrayList<com.lariflix.jemm.dtos.JellyfinMediaSource> sources = new java.util.ArrayList<>();
            sources.add(source);
            metadata.setMediaSources(sources);
            info = MediaTechInfo.fromMetadata(metadata);
        }
    }
}
