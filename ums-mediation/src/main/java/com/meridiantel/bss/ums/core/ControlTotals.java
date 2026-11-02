package com.meridiantel.bss.ums.core;

/**
 * Per-file control totals for revenue assurance.
 * Balance rule: input = rejected + duplicates + filtered + held + written
 * (an aggregated data record counts as 1 written - its earlier partials were counted as held).
 */
public class ControlTotals {

    public String stream;
    public String fileName;
    public long input;
    public long rejected;
    public long duplicates;
    public long filtered;
    public long held;
    public long written;      // billing + ocs (each record once)
    public long fraud;
    public long totalDurationSec;
    public long totalVolumeBytes;
    public long startMs = System.currentTimeMillis();
    public long endMs;
    public String status = "OK";

    public boolean isBalanced() {
        return input == rejected + duplicates + filtered + held + written;
    }

    public String toString() {
        return stream + "/" + fileName + " in=" + input + " rej=" + rejected + " dup=" + duplicates + " filt=" + filtered
                + " held=" + held + " out=" + written + " fraud=" + fraud + " balanced=" + isBalanced() + " status=" + status;
    }
}
