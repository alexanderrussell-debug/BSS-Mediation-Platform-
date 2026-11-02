package com.meridiantel.bss.ums.process;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import com.meridiantel.bss.ums.core.UmsConstants;
import com.meridiantel.bss.ums.core.UsageRecord;

/**
 * Drops records that must not reach billing. Returns the filter reason, or null to keep.
 * Filtered records are counted in control totals but not written anywhere.
 */
public class RecordFilter {

    private Set testMsisdns = new HashSet();

    public String filter(UsageRecord u) {
        if (testMsisdns.contains(u.getServedMsisdn())) {
            return "TEST_NUMBER";
        }
        String t = u.getRecordType();
        if ((UmsConstants.RT_VOICE_MO.equals(t) || UmsConstants.RT_VOICE_FWD.equals(t)) && u.getDurationSec() == 0) {
            return "UNANSWERED";
        }
        if (UmsConstants.RT_VOICE_MT.equals(t) && !u.isRoaming()) {
            return "MT_HOME_NOT_BILLED";
        }
        if (UmsConstants.RT_DATA.equals(t) && u.getVolumeBytes() <= 0) {
            return "ZERO_USAGE";
        }
        if (UmsConstants.RT_SMS_MO.equals(t) && !"DELIVERED".equals(u.getSmsStatus())) {
            return "SMS_NOT_DELIVERED";
        }
        if (UmsConstants.ZONE_SHORTCODE.equals(u.getZone())) {
            return "SERVICE_NUMBER";
        }
        return null;
    }

    /** comma separated, from ums.filter.testMsisdns */
    public void setTestMsisdns(String list) {
        testMsisdns = new HashSet();
        if (list == null) return;
        StringTokenizer st = new StringTokenizer(list, ",");
        while (st.hasMoreTokens()) {
            testMsisdns.add(st.nextToken().trim());
        }
    }
}
