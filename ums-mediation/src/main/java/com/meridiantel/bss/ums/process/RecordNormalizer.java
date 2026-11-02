package com.meridiantel.bss.ums.process;

import com.meridiantel.bss.ums.core.RejectException;
import com.meridiantel.bss.ums.core.StreamConfig;
import com.meridiantel.bss.ums.core.UmsConstants;
import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.util.MsisdnUtil;

/**
 * Brings numbers to international format without '+'.
 */
public class RecordNormalizer {

    public void normalize(UsageRecord u, StreamConfig cfg) throws RejectException {
        String served = MsisdnUtil.normalize(u.getServedMsisdn(), u.isServedInternational(), cfg.getCountry());
        if (served == null) {
            throw new RejectException(UmsConstants.R_NO_MSISDN, "Cannot normalise served MSISDN " + u.getServedMsisdn());
        }
        u.setServedMsisdn(served);

        if (u.getOtherParty() != null) {
            String other = MsisdnUtil.normalize(u.getOtherParty(), u.isOtherInternational(), cfg.getCountry());
            // keep raw value if it doesn't normalise (alphanumeric SMS senders etc.)
            if (other != null) {
                u.setOtherParty(other);
            }
        }
    }
}
