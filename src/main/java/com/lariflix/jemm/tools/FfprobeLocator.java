package com.lariflix.jemm.tools;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Resolves the ffprobe executable to use and verifies that it can actually run.
 *
 * <p>When a configured path is provided it is used as-is; otherwise the plain
 * command {@code ffprobe} is used and resolved via the system PATH. Availability
 * is checked once (via {@code ffprobe -version}) and cached.</p>
 */
public class FfprobeLocator {

    private static final long VERIFY_TIMEOUT_SECONDS = 10L;

    private final String executable;
    private Boolean available;
    private String verifyError;

    /**
     * @param configuredPath optional explicit path to the ffprobe binary; when
     *                       null or blank, {@code ffprobe} from PATH is used
     */
    public FfprobeLocator(String configuredPath) {
        this.executable = (configuredPath != null && !configuredPath.isBlank())
                ? configuredPath.trim()
                : "ffprobe";
    }

    /**
     * @return the executable command/path that will be invoked
     */
    public String getExecutable() {
        return executable;
    }

    /**
     * Verifies (once, then cached) that ffprobe can be launched.
     *
     * @return true when {@code ffprobe -version} ran successfully
     */
    public synchronized boolean isAvailable() {
        if (available == null) {
            available = verify();
        }
        return available;
    }

    /**
     * @return the error captured during verification, or null when available
     */
    public synchronized String getVerifyError() {
        isAvailable();
        return verifyError;
    }

    private boolean verify() {
        // If a concrete path was configured, make sure the file exists before spawning.
        if (!"ffprobe".equals(executable)) {
            File f = new File(executable);
            if (!f.exists() || f.isDirectory()) {
                verifyError = "Configured ffprobe path does not exist: " + executable;
                return false;
            }
        }
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(executable, "-version");
            pb.redirectErrorStream(true);
            process = pb.start();
            boolean finished = process.waitFor(VERIFY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                verifyError = "ffprobe -version timed out";
                return false;
            }
            int exit = process.exitValue();
            if (exit != 0) {
                verifyError = "ffprobe -version exited with code " + exit;
                return false;
            }
            return true;
        } catch (Exception ex) {
            verifyError = "ffprobe not found or not runnable (" + executable + "): " + ex.getMessage();
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
