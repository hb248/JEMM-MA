package com.lariflix.jemm.tools;

/**
 * Aggregate counters for batch tool runs.
 */
public class BatchJobResult {
    public int total;
    public int updated;
    public int skipped;
    public int failed;

    public String summary(String action) {
        return action + " finished.\n"
                + "Total: " + total + "\n"
                + "Updated: " + updated + "\n"
                + "Skipped: " + skipped + "\n"
                + "Failed: " + failed;
    }
}
