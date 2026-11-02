package com.meridiantel.bss.ums.core;

/**
 * Global constants. Record types match BillCore UDR "EVENT_TYPE" codes - do not change.
 */
public interface UmsConstants {

    // record types (BillCore event codes)
    String RT_VOICE_MO = "VMO";
    String RT_VOICE_MT = "VMT";
    String RT_VOICE_FWD = "VCF";
    String RT_DATA = "GPR";
    String RT_SMS_MO = "SMO";

    // stream types
    String STREAM_MSC_BER = "MSC_BER";
    String STREAM_PGW_CSV = "PGW_CSV";
    String STREAM_SMSC_PIPE = "SMSC_PIPE";

    // payment types
    String PREPAID = "PRE";
    String POSTPAID = "POST";

    // zones
    String ZONE_INCOMING = "INCOMING";
    String ZONE_DATA_HOME = "DATA_HOME";
    String ZONE_DATA_ROAM = "DATA_ROAM";
    String ZONE_SHORTCODE = "SHORTCODE";
    String ZONE_UNKNOWN = "UNKNOWN";

    // reject reason codes (see docs/ARCHITECTURE.md)
    String R_DECODE = "R00";
    String R_NO_MSISDN = "R01";
    String R_BAD_TIME = "R02";
    String R_BAD_DURATION = "R03";
    String R_TOO_LONG = "R04";
    String R_BAD_TYPE = "R05";
    String R_SUB_NOT_FOUND = "R10";
    String R_NO_ZONE = "R20";

    int MAX_DURATION_SEC = 86400;
}
