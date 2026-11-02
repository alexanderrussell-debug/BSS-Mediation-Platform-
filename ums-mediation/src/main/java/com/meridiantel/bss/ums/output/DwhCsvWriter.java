package com.meridiantel.bss.ums.output;

import java.io.IOException;

import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.util.DateUtil;
import com.meridiantel.bss.ums.util.StringUtil;

/**
 * Usage feed for the data warehouse (Teradata loader). All records, pre- and postpaid. UTC times.
 */
public class DwhCsvWriter extends OutputFile {

    public DwhCsvWriter(String dir, String name) throws IOException {
        super(dir, name);
    }

    protected void writeHeader() throws IOException {
        line("RECORD_TYPE,ACCOUNT_NO,MSISDN,OTHER_PARTY,START_UTC,DURATION,VOLUME_BYTES,ZONE,ROAMING,PARTNER,BAND,"
                + "RATE_PLAN,PAYMENT_TYPE,PARTIALS,SOURCE_STREAM,SOURCE_FILE,SOURCE_POS");
    }

    public void write(UsageRecord u) throws IOException {
        line(u.getRecordType() + "," + StringUtil.csv(u.getAccountNo()) + "," + u.getServedMsisdn() + ","
                + StringUtil.csv(u.getOtherParty()) + "," + DateUtil.format(u.getStartTime(), "yyyy-MM-dd HH:mm:ss", "UTC") + ","
                + u.getDurationSec() + "," + u.getVolumeBytes() + "," + u.getZone() + "," + (u.isRoaming() ? "Y" : "N") + ","
                + StringUtil.csv(u.getRoamingPartner()) + "," + u.getTariffBand() + "," + u.getRatePlan() + ","
                + u.getPaymentType() + "," + u.getPartialCount() + "," + u.getSourceStream() + "," + u.getSourceFile() + ","
                + u.getSourcePosition());
        count++;
    }
}
