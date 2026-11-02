package com.meridiantel.bss.ums.process;

import java.util.Date;

import com.meridiantel.bss.ums.core.RejectException;
import com.meridiantel.bss.ums.core.UmsConstants;
import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.util.StringUtil;

public class RecordValidator {

    public void validate(UsageRecord u) throws RejectException {
        Object err = u.getExtra().get("DECODE_ERROR");
        if (err != null) {
            throw new RejectException(UmsConstants.R_DECODE, "Decode error: " + err);
        }
        if (u.getRecordType() == null) {
            throw new RejectException(UmsConstants.R_BAD_TYPE, "Unknown record type " + u.getExtra().get("RAW_TYPE"));
        }
        if (StringUtil.isEmpty(u.getServedMsisdn())) {
            throw new RejectException(UmsConstants.R_NO_MSISDN, "Served MSISDN missing");
        }
        if (u.getStartTime() == null) {
            throw new RejectException(UmsConstants.R_BAD_TIME, "Start time missing");
        }
        if (u.getStartTime().getTime() > System.currentTimeMillis() + 86400000L) {
            throw new RejectException(UmsConstants.R_BAD_TIME, "Start time in the future: " + u.getStartTime());
        }
        if (u.getStartTime().before(new Date(System.currentTimeMillis() - 90L * 86400000L))) {
            // older than 90 days cannot be billed (bill cycle closed)
            throw new RejectException(UmsConstants.R_BAD_TIME, "Start time older than 90 days: " + u.getStartTime());
        }
        if (u.getDurationSec() < 0) {
            throw new RejectException(UmsConstants.R_BAD_DURATION, "Negative duration " + u.getDurationSec());
        }
        if (u.getDurationSec() > UmsConstants.MAX_DURATION_SEC) {
            throw new RejectException(UmsConstants.R_TOO_LONG, "Duration " + u.getDurationSec() + "s exceeds 24h");
        }
    }
}
