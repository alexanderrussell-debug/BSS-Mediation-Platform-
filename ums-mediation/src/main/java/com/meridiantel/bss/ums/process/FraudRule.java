package com.meridiantel.bss.ums.process;

import com.meridiantel.bss.ums.core.UsageRecord;

/**
 * Feeds the Fraud Management System (FMS) with high-risk usage.
 * Thresholds agreed with Revenue Assurance in 2015.
 */
public class FraudRule {

    private long longCallSec = 600;
    private long roamingCallSec = 3600;
    private long roamingDataBytes = 500L * 1024 * 1024;

    public boolean isSuspicious(UsageRecord u) {
        String z = u.getZone();
        if (("INTL_ROW".equals(z) || "INTL_SATELLITE".equals(z) || "SG_PREMIUM".equals(z)) && u.getDurationSec() >= longCallSec) {
            return true;
        }
        if (u.isRoaming() && u.getDurationSec() >= roamingCallSec && !"GPR".equals(u.getRecordType())) {
            return true;
        }
        if (u.isRoaming() && "GPR".equals(u.getRecordType()) && u.getVolumeBytes() >= roamingDataBytes) {
            return true;
        }
        return false;
    }

    public void setLongCallSec(long longCallSec) { this.longCallSec = longCallSec; }
    public void setRoamingCallSec(long roamingCallSec) { this.roamingCallSec = roamingCallSec; }
    public void setRoamingDataBytes(long roamingDataBytes) { this.roamingDataBytes = roamingDataBytes; }
}
