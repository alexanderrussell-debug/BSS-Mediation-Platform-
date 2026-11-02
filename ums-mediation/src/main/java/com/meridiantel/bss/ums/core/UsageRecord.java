package com.meridiantel.bss.ums.core;

import java.io.Serializable;
import java.util.Date;
import java.util.Hashtable;

/**
 * Normalised usage record - the "UDR". One class for all streams; fields not
 * relevant to a stream stay null/0.
 *
 * NOTE: Serializable because pending partial CDRs are persisted with ObjectOutputStream.
 * No serialVersionUID - adding a field breaks pending.ser (UMS-412).
 */
public class UsageRecord implements Serializable {

    // ---- source
    private String sourceStream;
    private String sourceFile;
    private int sourcePosition;
    private String rawData;

    // ---- decoded
    private String recordType;
    private String servedMsisdn;
    private boolean servedInternational;
    private String servedImsi;
    private String otherParty;
    private boolean otherInternational;
    private Date startTime;
    private long durationSec;
    private long volumeBytes;
    private String nodeId;
    private String servingMccMnc;
    private int causeCode;
    private String chargingId;
    private int partialSeq;
    private boolean partialFinal = true;
    private int partialCount = 1;
    private String apn;
    private String ratType;
    private String smsStatus;
    private int smsSegments;

    // ---- enrichment
    private String accountNo;
    private String ratePlan;
    private String paymentType;
    private String zone;
    private String tariffBand;
    private boolean roaming;
    private String roamingPartner;

    private Hashtable extra = new Hashtable();

    public String getSourceStream() { return sourceStream; }
    public void setSourceStream(String sourceStream) { this.sourceStream = sourceStream; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
    public int getSourcePosition() { return sourcePosition; }
    public void setSourcePosition(int sourcePosition) { this.sourcePosition = sourcePosition; }
    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }
    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }
    public String getServedMsisdn() { return servedMsisdn; }
    public void setServedMsisdn(String servedMsisdn) { this.servedMsisdn = servedMsisdn; }
    public boolean isServedInternational() { return servedInternational; }
    public void setServedInternational(boolean servedInternational) { this.servedInternational = servedInternational; }
    public String getServedImsi() { return servedImsi; }
    public void setServedImsi(String servedImsi) { this.servedImsi = servedImsi; }
    public String getOtherParty() { return otherParty; }
    public void setOtherParty(String otherParty) { this.otherParty = otherParty; }
    public boolean isOtherInternational() { return otherInternational; }
    public void setOtherInternational(boolean otherInternational) { this.otherInternational = otherInternational; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public long getDurationSec() { return durationSec; }
    public void setDurationSec(long durationSec) { this.durationSec = durationSec; }
    public long getVolumeBytes() { return volumeBytes; }
    public void setVolumeBytes(long volumeBytes) { this.volumeBytes = volumeBytes; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getServingMccMnc() { return servingMccMnc; }
    public void setServingMccMnc(String servingMccMnc) { this.servingMccMnc = servingMccMnc; }
    public int getCauseCode() { return causeCode; }
    public void setCauseCode(int causeCode) { this.causeCode = causeCode; }
    public String getChargingId() { return chargingId; }
    public void setChargingId(String chargingId) { this.chargingId = chargingId; }
    public int getPartialSeq() { return partialSeq; }
    public void setPartialSeq(int partialSeq) { this.partialSeq = partialSeq; }
    public boolean isPartialFinal() { return partialFinal; }
    public void setPartialFinal(boolean partialFinal) { this.partialFinal = partialFinal; }
    public int getPartialCount() { return partialCount; }
    public void setPartialCount(int partialCount) { this.partialCount = partialCount; }
    public String getApn() { return apn; }
    public void setApn(String apn) { this.apn = apn; }
    public String getRatType() { return ratType; }
    public void setRatType(String ratType) { this.ratType = ratType; }
    public String getSmsStatus() { return smsStatus; }
    public void setSmsStatus(String smsStatus) { this.smsStatus = smsStatus; }
    public int getSmsSegments() { return smsSegments; }
    public void setSmsSegments(int smsSegments) { this.smsSegments = smsSegments; }
    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public String getRatePlan() { return ratePlan; }
    public void setRatePlan(String ratePlan) { this.ratePlan = ratePlan; }
    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getTariffBand() { return tariffBand; }
    public void setTariffBand(String tariffBand) { this.tariffBand = tariffBand; }
    public boolean isRoaming() { return roaming; }
    public void setRoaming(boolean roaming) { this.roaming = roaming; }
    public String getRoamingPartner() { return roamingPartner; }
    public void setRoamingPartner(String roamingPartner) { this.roamingPartner = roamingPartner; }
    public Hashtable getExtra() { return extra; }

    public String toString() {
        return recordType + "[" + servedMsisdn + "->" + otherParty + " @" + startTime + " dur=" + durationSec
                + " vol=" + volumeBytes + " zone=" + zone + " src=" + sourceFile + "#" + sourcePosition + "]";
    }
}
