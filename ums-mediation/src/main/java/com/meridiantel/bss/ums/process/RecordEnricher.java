package com.meridiantel.bss.ums.process;

import com.meridiantel.bss.ums.core.RejectException;
import com.meridiantel.bss.ums.core.StreamConfig;
import com.meridiantel.bss.ums.core.UmsConstants;
import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.reference.RatingZoneTable;
import com.meridiantel.bss.ums.reference.RoamingPartnerTable;
import com.meridiantel.bss.ums.reference.SubscriberCache;
import com.meridiantel.bss.ums.util.DateUtil;
import com.meridiantel.bss.ums.util.MsisdnUtil;

/**
 * Adds account, rate plan, zone, roaming and tariff band.
 */
public class RecordEnricher {

    private SubscriberCache subscriberCache;
    private RatingZoneTable ratingZoneTable;
    private RoamingPartnerTable roamingPartnerTable;
    private int peakStartHour = 8;
    private int peakEndHour = 20;

    public void enrich(UsageRecord u, StreamConfig cfg) throws RejectException {
        String[] sub = subscriberCache.lookup(u.getServedMsisdn());
        if (sub == null) {
            throw new RejectException(UmsConstants.R_SUB_NOT_FOUND, "Subscriber " + u.getServedMsisdn() + " not in reference data");
        }
        u.setAccountNo(sub[0]);
        u.setRatePlan(sub[1]);
        u.setPaymentType(sub[2]);

        if (u.getServingMccMnc() != null && !roamingPartnerTable.isHome(u.getServingMccMnc())) {
            u.setRoaming(true);
            u.setRoamingPartner(roamingPartnerTable.partner(u.getServingMccMnc()));
        }

        String t = u.getRecordType();
        if (UmsConstants.RT_VOICE_MT.equals(t)) {
            u.setZone(UmsConstants.ZONE_INCOMING);
        } else if (UmsConstants.RT_DATA.equals(t)) {
            u.setZone(u.isRoaming() ? UmsConstants.ZONE_DATA_ROAM : UmsConstants.ZONE_DATA_HOME);
        } else {
            if (MsisdnUtil.isShortCode(u.getOtherParty())) {
                u.setZone(UmsConstants.ZONE_SHORTCODE);
            } else {
                String zone = ratingZoneTable.lookup(u.getOtherParty());
                if (zone == null) {
                    throw new RejectException(UmsConstants.R_NO_ZONE, "No rating zone for " + u.getOtherParty());
                }
                u.setZone(zone);
            }
        }

        int hour = DateUtil.hourOfDay(u.getStartTime(), cfg.getTimezone());
        u.setTariffBand(hour >= peakStartHour && hour < peakEndHour ? "PEAK" : "OFFPEAK");
    }

    public void setSubscriberCache(SubscriberCache subscriberCache) { this.subscriberCache = subscriberCache; }
    public void setRatingZoneTable(RatingZoneTable ratingZoneTable) { this.ratingZoneTable = ratingZoneTable; }
    public void setRoamingPartnerTable(RoamingPartnerTable roamingPartnerTable) { this.roamingPartnerTable = roamingPartnerTable; }
    public void setPeakStartHour(int peakStartHour) { this.peakStartHour = peakStartHour; }
    public void setPeakEndHour(int peakEndHour) { this.peakEndHour = peakEndHour; }
}
