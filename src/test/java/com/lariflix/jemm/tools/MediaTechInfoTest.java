package com.lariflix.jemm.tools;

import com.lariflix.jemm.dtos.JellyfinItemMetadata;
import com.lariflix.jemm.dtos.JellyfinMediaSource;
import com.lariflix.jemm.dtos.JellyfinMediaStream;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MediaTechInfoTest {

    @Test
    public void needsProbeWhenNoDimensions() {
        MediaTechInfo info = new MediaTechInfo();
        assertTrue(info.needsProbe());
    }

    @Test
    public void needsProbeForVideoMissingFpsOrBitrate() {
        // Video with dimensions but no fps/bitrate should still need a probe.
        MediaTechInfo info = videoInfo(1920, 1080, 0, 0);
        assertTrue(info.needsProbe());
    }

    @Test
    public void completeVideoDoesNotNeedProbe() {
        MediaTechInfo info = videoInfo(1920, 1080, 30, 5_000_000);
        assertFalse(info.needsProbe());
    }

    @Test
    public void mergeFillsOnlyMissingFields() {
        MediaTechInfo info = videoInfo(1920, 1080, 0, 0);
        assertTrue(info.needsProbe());

        FfprobeResult probe = new FfprobeResult();
        probe.setWidth(640);   // should be ignored, width already set
        probe.setHeight(480);  // should be ignored, height already set
        probe.setFrameRate(29.97);
        probe.setBitRate(4_000_000);
        info.merge(probe);

        assertEquals(1920, info.getWidth());
        assertEquals(1080, info.getHeight());
        assertEquals(29.97, info.getFrameRate(), 0.001);
        assertEquals(4_000_000L, info.getBitRate());
        assertFalse(info.needsProbe());
    }

    @Test
    public void mergeNullIsSafe() {
        MediaTechInfo info = new MediaTechInfo();
        info.merge(null);
        assertTrue(info.needsProbe());
    }

    @Test
    public void audioDetectionFromStreams() {
        // videoInfo() adds only a video stream -> audio is known and absent.
        MediaTechInfo silent = videoInfo(1920, 1080, 30, 5_000_000);
        assertTrue(silent.isAudioKnown());
        assertFalse(silent.isHasAudio());
    }

    @Test
    public void audioUnknownWithoutStreams() {
        MediaTechInfo info = new MediaTechInfo();
        assertFalse(info.isAudioKnown());
    }

    @Test
    public void mergeFillsAudioWhenUnknown() {
        MediaTechInfo info = new MediaTechInfo();
        assertFalse(info.isAudioKnown());
        FfprobeResult probe = new FfprobeResult();
        probe.setWidth(1920);
        probe.setHeight(1080);
        probe.setAudioKnown(true);
        probe.setHasAudio(false);
        info.merge(probe);
        assertTrue(info.isAudioKnown());
        assertFalse(info.isHasAudio());
    }

    private MediaTechInfo videoInfo(int w, int h, double fps, long bitrate) {
        JellyfinItemMetadata metadata = new JellyfinItemMetadata();
        metadata.setMediaType("Video");
        metadata.setType("Movie");
        JellyfinMediaStream stream = new JellyfinMediaStream();
        stream.setType("Video");
        stream.setWidth(w);
        stream.setHeight(h);
        if (fps > 0) {
            stream.setAverageFrameRate(fps);
        }
        if (bitrate > 0) {
            stream.setBitRate(bitrate);
        }
        stream.setIsDefault(true);
        ArrayList<JellyfinMediaStream> streams = new ArrayList<>();
        streams.add(stream);
        JellyfinMediaSource source = new JellyfinMediaSource();
        source.setMediaStreams(streams);
        ArrayList<JellyfinMediaSource> sources = new ArrayList<>();
        sources.add(source);
        metadata.setMediaSources(sources);
        return MediaTechInfo.fromMetadata(metadata);
    }
}
