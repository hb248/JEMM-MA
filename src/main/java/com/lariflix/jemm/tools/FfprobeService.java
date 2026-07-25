package com.lariflix.jemm.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Runs ffprobe against a media input (local path or URL) and parses its JSON
 * output into a {@link FfprobeResult}. Used as the single fallback for Auto Tags
 * when Jellyfin's API lacks technical data.
 */
public class FfprobeService {

    private static final long PROBE_TIMEOUT_SECONDS = 20L;

    private final String executable;
    private final ObjectMapper mapper = new ObjectMapper();

    public FfprobeService(String executable) {
        this.executable = (executable == null || executable.isBlank()) ? "ffprobe" : executable.trim();
    }

    /**
     * Probes the given media input.
     *
     * @param input a local file path or an http(s) URL ffprobe can read
     * @return parsed technical facts (fields are 0 when unknown)
     * @throws IOException          when the ffprobe process fails to run, times out or exits non-zero
     * @throws InterruptedException when the waiting thread is interrupted
     */
    public FfprobeResult probe(String input) throws IOException, InterruptedException {
        if (input == null || input.isBlank()) {
            throw new IOException("No media input to probe");
        }
        ProcessBuilder pb = new ProcessBuilder(
                executable,
                "-v", "quiet",
                "-print_format", "json",
                "-show_streams",
                "-show_format",
                input);
        Process process = pb.start();
        String stdout;
        try (InputStream is = process.getInputStream()) {
            stdout = readFully(is);
            boolean finished = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("ffprobe timed out after " + PROBE_TIMEOUT_SECONDS + "s for input: " + input);
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        int exit = process.exitValue();
        if (exit != 0) {
            throw new IOException("ffprobe exited with code " + exit + " for input: " + input);
        }
        if (stdout == null || stdout.isBlank()) {
            throw new IOException("ffprobe produced no output for input: " + input);
        }
        return parse(stdout);
    }

    /**
     * Parses ffprobe JSON output. Package-visible for testing.
     *
     * @param json the ffprobe {@code -print_format json} output
     * @return parsed technical facts
     * @throws IOException when the JSON cannot be parsed
     */
    FfprobeResult parse(String json) throws IOException {
        FfprobeResult result = new FfprobeResult();
        JsonNode root = mapper.readTree(json);

        JsonNode streams = root.get("streams");
        JsonNode chosen = null;
        if (streams != null && streams.isArray() && streams.size() > 0) {
            // A non-empty stream listing lets us determine audio presence.
            result.setAudioKnown(true);
            // Prefer the first video stream; fall back to any stream carrying dimensions.
            for (JsonNode stream : streams) {
                String codecType = text(stream, "codec_type");
                if ("audio".equalsIgnoreCase(codecType)) {
                    result.setHasAudio(true);
                }
                if (chosen == null && "video".equalsIgnoreCase(codecType)) {
                    chosen = stream;
                }
            }
            if (chosen == null) {
                for (JsonNode stream : streams) {
                    if (stream.hasNonNull("width") && stream.hasNonNull("height")) {
                        chosen = stream;
                        break;
                    }
                }
            }
        }

        if (chosen != null) {
            result.setWidth(intValue(chosen, "width"));
            result.setHeight(intValue(chosen, "height"));
            double fps = parseFrameRate(text(chosen, "avg_frame_rate"));
            if (fps <= 0) {
                fps = parseFrameRate(text(chosen, "r_frame_rate"));
            }
            result.setFrameRate(fps);
            long streamBitRate = longValue(chosen, "bit_rate");
            if (streamBitRate > 0) {
                result.setBitRate(streamBitRate);
            }
        }

        JsonNode format = root.get("format");
        if (format != null) {
            if (result.getBitRate() <= 0) {
                result.setBitRate(longValue(format, "bit_rate"));
            }
            result.setFileSize(longValue(format, "size"));
        }

        return result;
    }

    /**
     * Parses an ffprobe frame-rate value such as "30000/1001" or "25/1".
     *
     * @param value the raw fraction string
     * @return frames per second, or 0 when not parseable
     */
    static double parseFrameRate(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String trimmed = value.trim();
        try {
            int slash = trimmed.indexOf('/');
            if (slash < 0) {
                double single = Double.parseDouble(trimmed);
                return single > 0 ? single : 0;
            }
            double num = Double.parseDouble(trimmed.substring(0, slash).trim());
            double den = Double.parseDouble(trimmed.substring(slash + 1).trim());
            if (den == 0) {
                return 0;
            }
            double fps = num / den;
            return fps > 0 ? fps : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static int intValue(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static long longValue(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0L, Long.parseLong(raw.trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String readFully(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
