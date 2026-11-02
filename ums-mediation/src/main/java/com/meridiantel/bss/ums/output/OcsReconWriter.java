package com.meridiantel.bss.ums.output;

import java.io.IOException;

import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.util.DateUtil;

/**
 * Prepaid usage is charged online by the OCS. Mediation only sends it for reconciliation.
 */
public class OcsReconWriter extends OutputFile {

    public OcsReconWriter(String dir, String name) throws IOException {
        super(dir, name);
    }

    public void write(UsageRecord u) throws IOException {
        line(u.getServedMsisdn() + "|" + u.getRecordType() + "|" + DateUtil.format(u.getStartTime(), "yyyyMMddHHmmss", "UTC")
                + "|" + u.getDurationSec() + "|" + u.getVolumeBytes() + "|" + u.getZone());
        count++;
    }
}
