package com.meridiantel.bss.ums.decode;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.meridiantel.bss.ums.core.StreamConfig;
import com.meridiantel.bss.ums.core.UmsConstants;
import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.util.DateUtil;

/**
 * MSC voice CDRs, BER encoded. Each record is a constructed [0] (0xA0) containing:
 * <pre>
 *   0x80 recordType        INTEGER (0=MOC, 1=MTC, 2=CF)
 *   0x81 servedMSISDN      AddressString
 *   0x82 calledNumber      AddressString
 *   0x83 callingNumber     AddressString
 *   0x84 answerTime        TimeStamp
 *   0x85 callDuration      INTEGER (seconds)
 *   0x86 mscIdentification IA5String
 *   0x87 servingPLMN       IA5String (MCC+MNC)
 *   0x88 causeForTerm      INTEGER
 *   0x89 recordSequence    INTEGER
 *   0x8A servedIMSI        TBCD (no TON octet - decoded with a dummy prefix)
 * </pre>
 * Unknown tags are skipped.
 */
public class MscBerDecoder implements Decoder {

    private static final Logger log = Logger.getLogger(MscBerDecoder.class);

    public List decode(File file, StreamConfig cfg) throws IOException {
        List result = new ArrayList();
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            BerTlvReader r = new BerTlvReader(in);
            int pos = 0;
            while (true) {
                int tag = r.readTag();
                if (tag < 0) break;
                // 0x00 padding at end of block (Ericsson pads blocks to 2048 bytes)
                if (tag == 0x00) continue;
                int len = r.readLength();
                byte[] body = r.readValue(len);
                pos++;
                UsageRecord u = new UsageRecord();
                u.setSourceStream(cfg.getName());
                u.setSourceFile(file.getName());
                u.setSourcePosition(pos);
                u.setRawData(BerTlvReader.hex(body));
                if (tag != 0xA0) {
                    u.getExtra().put("DECODE_ERROR", "Unexpected record tag 0x" + Integer.toHexString(tag));
                    result.add(u);
                    continue;
                }
                try {
                    decodeRecord(body, u, cfg);
                } catch (Exception e) {
                    u.getExtra().put("DECODE_ERROR", e.getMessage() == null ? e.toString() : e.getMessage());
                }
                result.add(u);
            }
        } catch (IOException e) {
            // truncated file: keep what we have
            log.error("BER decode stopped in " + file.getName() + ": " + e.getMessage());
        } finally {
            in.close();
        }
        return result;
    }

    private void decodeRecord(byte[] body, UsageRecord u, StreamConfig cfg) throws Exception {
        BerTlvReader r = new BerTlvReader(new ByteArrayInputStream(body));
        String calling = null;
        String called = null;
        boolean callingIntl = false;
        boolean calledIntl = false;
        int type = -1;
        while (true) {
            int tag = r.readTag();
            if (tag < 0) break;
            int len = r.readLength();
            byte[] v = r.readValue(len);
            switch (tag) {
                case 0x80: type = (int) BerTlvReader.decodeInteger(v); break;
                case 0x81:
                    u.setServedMsisdn(BerTlvReader.decodeAddress(v));
                    u.setServedInternational(BerTlvReader.isInternational(v));
                    break;
                case 0x82: called = BerTlvReader.decodeAddress(v); calledIntl = BerTlvReader.isInternational(v); break;
                case 0x83: calling = BerTlvReader.decodeAddress(v); callingIntl = BerTlvReader.isInternational(v); break;
                case 0x84:
                    u.setStartTime(DateUtil.parse(BerTlvReader.decodeTimestamp(v), "yyMMddHHmmss", cfg.getTimezone()));
                    break;
                case 0x85: u.setDurationSec(BerTlvReader.decodeInteger(v)); break;
                case 0x86: u.setNodeId(BerTlvReader.decodeString(v)); break;
                case 0x87: u.setServingMccMnc(BerTlvReader.decodeString(v)); break;
                case 0x88: u.setCauseCode((int) BerTlvReader.decodeInteger(v)); break;
                case 0x89: u.getExtra().put("SEQ", String.valueOf(BerTlvReader.decodeInteger(v))); break;
                case 0x8A:
                    byte[] withPrefix = new byte[v.length + 1];
                    System.arraycopy(v, 0, withPrefix, 1, v.length);
                    u.setServedImsi(BerTlvReader.decodeAddress(withPrefix));
                    break;
                default:
                    log.debug("Skipping unknown tag 0x" + Integer.toHexString(tag));
            }
        }
        if (type == 0) {
            u.setRecordType(UmsConstants.RT_VOICE_MO);
            u.setOtherParty(called);
            u.setOtherInternational(calledIntl);
        } else if (type == 1) {
            u.setRecordType(UmsConstants.RT_VOICE_MT);
            u.setOtherParty(calling);
            u.setOtherInternational(callingIntl);
        } else if (type == 2) {
            u.setRecordType(UmsConstants.RT_VOICE_FWD);
            u.setOtherParty(called);
            u.setOtherInternational(calledIntl);
        } else {
            u.setRecordType(null); // validator rejects R05
            u.getExtra().put("RAW_TYPE", String.valueOf(type));
        }
    }
}
