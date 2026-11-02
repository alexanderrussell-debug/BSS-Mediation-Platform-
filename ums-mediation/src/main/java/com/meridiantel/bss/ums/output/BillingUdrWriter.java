package com.meridiantel.bss.ums.output;

import java.io.IOException;
import java.util.Date;

import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.util.DateUtil;
import com.meridiantel.bss.ums.util.StringUtil;

/**
 * BillCore UDR v2 fixed-width file (postpaid rating).
 * <pre>
 * H seq(8) created(14) stream(10) "UDR02"
 * D type(3) account(12) msisdn(15) otherParty(20) start(14) duration(8) volumeKb(10) zone(12)
 *   roaming(1) partner(12) band(7) ratePlan(10) recordId(24)
 * T count(9) totalDuration(12) totalVolumeKb(14) checksum(10)
 * </pre>
 * Start time is local time of the stream.
 */
public class BillingUdrWriter extends OutputFile {

    private long fileSeq;
    private String stream;
    private String timezone;
    private long totalDuration;
    private long totalVolumeKb;
    private long checksum;

    public BillingUdrWriter(String dir, String name, long fileSeq, String stream, String timezone) throws IOException {
        super(dir, name);
        this.fileSeq = fileSeq;
        this.stream = stream;
        this.timezone = timezone;
        line("H" + StringUtil.lpad(fileSeq, 8) + DateUtil.format(new Date(), "yyyyMMddHHmmss", timezone)
                + StringUtil.rpad(stream, 10) + "UDR02");
    }

    public void write(UsageRecord u) throws IOException {
        long kb = (u.getVolumeBytes() + 1023) / 1024;
        String rec = "D"
                + StringUtil.rpad(u.getRecordType(), 3)
                + StringUtil.rpad(u.getAccountNo(), 12)
                + StringUtil.rpad(u.getServedMsisdn(), 15)
                + StringUtil.rpad(u.getOtherParty(), 20)
                + DateUtil.format(u.getStartTime(), "yyyyMMddHHmmss", timezone)
                + StringUtil.lpad(u.getDurationSec(), 8)
                + StringUtil.lpad(kb, 10)
                + StringUtil.rpad(u.getZone(), 12)
                + (u.isRoaming() ? "Y" : "N")
                + StringUtil.rpad(u.getRoamingPartner(), 12)
                + StringUtil.rpad(u.getTariffBand(), 7)
                + StringUtil.rpad(u.getRatePlan(), 10)
                + StringUtil.rpad(u.getSourceStream() + "-" + u.getSourcePosition(), 24);
        line(rec);
        count++;
        totalDuration += u.getDurationSec();
        totalVolumeKb += kb;
        checksum += rec.hashCode();
    }

    protected void writeTrailer() throws IOException {
        line("T" + StringUtil.lpad(count, 9) + StringUtil.lpad(totalDuration, 12) + StringUtil.lpad(totalVolumeKb, 14)
                + StringUtil.lpad(Math.abs(checksum % 10000000000L), 10));
    }
}
