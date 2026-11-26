package com.meridiantel.bss.ums.process;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.meridiantel.bss.ums.core.UsageRecord;

/**
 * Correlates partial data CDRs (same charging id) into one session record.
 * Partials wait in memory (persisted to state/pending.ser) until the final record arrives.
 * An existing pending.ser that cannot be read stops startup (see StateFiles).
 *
 * TODO flush sessions whose final record never arrives (UMS-390) - they are never billed today.
 */
public class PartialCdrAggregator {

    private static final Logger log = Logger.getLogger(PartialCdrAggregator.class);

    private String stateFile;
    private Map pending = new HashMap(); // chargingId -> List<UsageRecord>

    /**
     * @return the aggregated session record if u is the final partial, otherwise null (held)
     */
    public synchronized UsageRecord add(UsageRecord u) {
        String key = u.getSourceStream() + "|" + u.getChargingId();
        List parts = (List) pending.get(key);
        if (parts == null) {
            parts = new ArrayList();
        }
        parts.add(u);

        if (!u.isPartialFinal()) {
            pending.put(key, parts);
            return null;
        }
        pending.remove(key);

        Collections.sort(parts, new Comparator() {
            public int compare(Object a, Object b) {
                return ((UsageRecord) a).getPartialSeq() - ((UsageRecord) b).getPartialSeq();
            }
        });

        UsageRecord first = (UsageRecord) parts.get(0);
        long totalDuration = 0;
        int totalVolume = 0; // BillCore volume field is KB, 10 digits - plenty
        for (int i = 0; i < parts.size(); i++) {
            UsageRecord p = (UsageRecord) parts.get(i);
            totalDuration += p.getDurationSec();
            totalVolume += p.getVolumeBytes();
        }

        u.setStartTime(first.getStartTime());
        u.setDurationSec(totalDuration);
        u.setVolumeBytes(totalVolume);
        u.setPartialCount(parts.size());
        if (parts.size() > 1) {
            log.debug("Aggregated " + parts.size() + " partials for charging id " + u.getChargingId());
        }
        return u;
    }

    public synchronized int pendingSessions() {
        return pending.size();
    }

    public synchronized void load() {
        File f = new File(stateFile);
        if (!f.exists()) return;
        try {
            Map loaded = (Map) StateFiles.read(f);
            checkContent(loaded);
            pending = loaded;
            log.info("Loaded " + pending.size() + " pending data sessions");
        } catch (Exception e) {
            pending = new HashMap();
            StateFiles.handleLoadFailure(log, f, "pending partials", e);
        }
    }

    /** Fails on content that would otherwise only blow up (ClassCastException) when the final partial arrives. */
    private static void checkContent(Map loaded) {
        if (loaded == null) {
            throw new IllegalStateException("file contains null");
        }
        for (Iterator it = loaded.entrySet().iterator(); it.hasNext();) {
            Map.Entry e = (Map.Entry) it.next();
            if (!(e.getKey() instanceof String) || !(e.getValue() instanceof List)) {
                throw new IllegalStateException("unexpected entry " + e.getKey());
            }
            List parts = (List) e.getValue();
            for (int i = 0; i < parts.size(); i++) {
                if (!(parts.get(i) instanceof UsageRecord)) {
                    throw new IllegalStateException("unexpected partial for " + e.getKey() + ": " + parts.get(i));
                }
            }
        }
    }

    public synchronized void save() {
        try {
            StateFiles.writeAtomically(new File(stateFile), pending);
        } catch (Exception e) {
            log.error("Could not save pending partials", e);
        }
    }

    public void setStateFile(String stateFile) { this.stateFile = stateFile; }
}
