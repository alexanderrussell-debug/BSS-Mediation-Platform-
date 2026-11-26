package com.meridiantel.bss.ums.process;

import java.io.File;
import java.util.HashSet;

import org.apache.log4j.Logger;

import com.meridiantel.bss.ums.core.UmsConstants;
import com.meridiantel.bss.ums.core.UsageRecord;

/**
 * Duplicate detection across all streams. Keys are kept in memory and saved to
 * state/dedup.ser after each file. An existing dedup.ser that cannot be read stops startup (see StateFiles).
 *
 * TODO purge keys older than 90 days (UMS-355). Heap is 6 GB now because of this.
 */
public class DuplicateChecker {

    private static final Logger log = Logger.getLogger(DuplicateChecker.class);

    private String stateFile;
    private HashSet keys = new HashSet();

    public synchronized void load() {
        File f = new File(stateFile);
        if (!f.exists()) {
            log.info("No dedup state at " + stateFile + ", starting empty");
            return;
        }
        try {
            keys = (HashSet) StateFiles.read(f);
            if (keys == null) {
                throw new IllegalStateException("file contains null");
            }
            log.info("Loaded " + keys.size() + " dedup keys");
        } catch (Exception e) {
            keys = new HashSet();
            StateFiles.handleLoadFailure(log, f, "dedup", e);
        }
    }

    public synchronized void save() {
        try {
            StateFiles.writeAtomically(new File(stateFile), keys);
        } catch (Exception e) {
            log.error("Could not save dedup state", e);
        }
    }

    /** Registers the record and returns true if it was already seen. */
    public synchronized boolean isDuplicate(UsageRecord u) {
        String k = key(u);
        if (keys.contains(k)) {
            log.debug("Duplicate: " + k);
            return true;
        }
        keys.add(k);
        return false;
    }

    protected String key(UsageRecord u) {
        String t = u.getRecordType();
        if (UmsConstants.RT_DATA.equals(t)) {
            return t + "|" + u.getChargingId() + "|" + u.getPartialSeq();
        } else if (UmsConstants.RT_SMS_MO.equals(t)) {
            return t + "|" + u.getServedMsisdn() + "|" + u.getOtherParty() + "|" + u.getStartTime().getTime();
        }
        return t + "|" + u.getServedMsisdn() + "|" + u.getStartTime().getTime() + "|" + u.getDurationSec() + "|" + u.getNodeId();
    }

    public synchronized int size() {
        return keys.size();
    }

    public void setStateFile(String stateFile) { this.stateFile = stateFile; }
}
