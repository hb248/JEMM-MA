package com.lariflix.jemm.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FfprobeServiceTest {

    private final FfprobeService service = new FfprobeService("ffprobe");

    @Test
    public void parsesVideoStreamAndFormat() throws Exception {
        String json = "{"
                + "\"streams\":["
                + "{\"codec_type\":\"audio\",\"bit_rate\":\"128000\"},"
                + "{\"codec_type\":\"video\",\"width\":1920,\"height\":1080,"
                + "\"avg_frame_rate\":\"30000/1001\",\"r_frame_rate\":\"30000/1001\",\"bit_rate\":\"5000000\"}"
                + "],"
                + "\"format\":{\"size\":\"123456789\",\"bit_rate\":\"5200000\"}"
                + "}";

        FfprobeResult result = service.parse(json);
        assertEquals(1920, result.getWidth());
        assertEquals(1080, result.getHeight());
        assertEquals(29.97, result.getFrameRate(), 0.01);
        assertEquals(5_000_000L, result.getBitRate());
        assertEquals(123_456_789L, result.getFileSize());
        assertTrue(result.hasAnyData());
    }

    @Test
    public void fallsBackToFormatBitrateWhenStreamMissing() throws Exception {
        String json = "{"
                + "\"streams\":[{\"codec_type\":\"video\",\"width\":640,\"height\":480,"
                + "\"avg_frame_rate\":\"25/1\"}],"
                + "\"format\":{\"size\":\"1000\",\"bit_rate\":\"800000\"}"
                + "}";

        FfprobeResult result = service.parse(json);
        assertEquals(640, result.getWidth());
        assertEquals(480, result.getHeight());
        assertEquals(25.0, result.getFrameRate(), 0.001);
        assertEquals(800_000L, result.getBitRate());
        assertEquals(1000L, result.getFileSize());
    }

    @Test
    public void picksDimensionedStreamWhenNoVideoType() throws Exception {
        String json = "{\"streams\":[{\"codec_type\":\"data\",\"width\":3000,\"height\":2000}]}";
        FfprobeResult result = service.parse(json);
        assertEquals(3000, result.getWidth());
        assertEquals(2000, result.getHeight());
    }

    @Test
    public void emptyStreamsYieldsNoData() throws Exception {
        FfprobeResult result = service.parse("{\"streams\":[],\"format\":{}}");
        assertFalse(result.hasAnyData());
    }

    @Test
    public void frameRateParsing() {
        assertEquals(29.97, FfprobeService.parseFrameRate("30000/1001"), 0.01);
        assertEquals(25.0, FfprobeService.parseFrameRate("25/1"), 0.001);
        assertEquals(24.0, FfprobeService.parseFrameRate("24"), 0.001);
        assertEquals(0.0, FfprobeService.parseFrameRate("0/0"), 0.001);
        assertEquals(0.0, FfprobeService.parseFrameRate(""), 0.001);
        assertEquals(0.0, FfprobeService.parseFrameRate(null), 0.001);
    }
}
