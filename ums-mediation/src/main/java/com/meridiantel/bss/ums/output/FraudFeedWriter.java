package com.meridiantel.bss.ums.output;

import java.io.IOException;

import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.util.DateUtil;

/** Near-real-time feed for the FMS. */
public class FraudFeedWriter extends OutputFile {

    public FraudFeedWriter(String dir, String name) throws IOException {
        super(dir, name);
    }

    public void write(UsageRecord u) throws IOException {
        line(u.getServedMsisdn() + ";" + u.getAccountNo() + ";" + u.getRecordType() + ";" + u.getOtherParty() + ";"
                + DateUtil.format(u.getStartTime(), "yyyyMMddHHmmss", "UTC") + ";" + u.getDurationSec() + ";"
                + u.getVolumeBytes() + ";" + u.getZone() + ";" + (u.isRoaming() ? u.getRoamingPartner() : ""));
        count++;
    }
}
