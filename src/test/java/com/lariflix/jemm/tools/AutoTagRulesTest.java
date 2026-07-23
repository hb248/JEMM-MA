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
        assertEquals(ManagedAutoTags.SQUARE, rules.orientationTag(info, null));

        MediaTechInfo nearSquare = tech(1050, 1000, 30, 8_000_000, true, false);
        assertEquals(ManagedAutoTags.SQUARE, rules.orientationTag(nearSquare, null));

        MediaTechInfo vertical = tech(1080, 1920, 30, 8_000_000, true, false);
        assertEquals(ManagedAutoTags.VERTICAL, rules.orientationTag(vertical, null));
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
