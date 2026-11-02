package com.meridiantel.bss.ums.decode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.meridiantel.bss.ums.core.StreamConfig;
import com.meridiantel.bss.ums.core.UmsConstants;
import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.util.DateUtil;

/**
 * PGW/GGSN data CDRs exported as CSV by the packet core vendor's CG.
 * <pre>
 * RECORD_TYPE,CHARGING_ID,SEQ_NO,SERVED_IMSI,SERVED_MSISDN,APN,START_TIME,DURATION,UPLINK_BYTES,DOWNLINK_BYTES,CAUSE,SERVING_PLMN,RAT
 * </pre>
 * CAUSE: 0 normalRelease, 4 abnormalRelease (final) / 16 volumeLimit, 17 timeLimit, 18 servingNodeChange (partial)
 */
public class PgwCsvDecoder implements Decoder {

    private static final int FIELDS = 13;

    public List decode(File file, StreamConfig cfg) throws IOException {
        List result = new ArrayList();
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            String line;
            int pos = 0;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("RECORD_TYPE") || line.trim().length() == 0) {
                    continue;
                }
                pos++;
                UsageRecord u = new UsageRecord();
                u.setSourceStream(cfg.getName());
                u.setSourceFile(file.getName());
                u.setSourcePosition(pos);
                u.setRawData(line);
                String[] f = line.split(",");
                if (f.length != FIELDS) {
                    u.getExtra().put("DECODE_ERROR", "Expected " + FIELDS + " fields, got " + f.length);
                    result.add(u);
                    continue;
                }
                try {
                    if (!"PGW".equals(f[0]) && !"SGW".equals(f[0]) && !"GGSN".equals(f[0])) {
                        u.setRecordType(null);
                    } else {
                        u.setRecordType(UmsConstants.RT_DATA);
                    }
                    u.setChargingId(f[1]);
                    u.setPartialSeq(Integer.parseInt(f[2]));
                    u.setServedImsi(f[3]);
                    u.setServedMsisdn(f[4]);
                    u.setServedInternational(true);
                    u.setApn(f[5]);
                    u.setStartTime(DateUtil.parse(f[6], "yyyy-MM-dd HH:mm:ss", cfg.getTimezone()));
                    u.setDurationSec(Long.parseLong(f[7]));
                    u.setVolumeBytes(Long.parseLong(f[8]) + Long.parseLong(f[9]));
                    int cause = Integer.parseInt(f[10]);
                    u.setCauseCode(cause);
                    u.setPartialFinal(cause == 0 || cause == 4);
                    u.setServingMccMnc(f[11]);
                    u.setRatType(f[12]);
                    u.setNodeId("PGW");
                } catch (Exception e) {
                    u.getExtra().put("DECODE_ERROR", e.toString());
                }
                result.add(u);
            }
        } finally {
            br.close();
        }
        return result;
    }
}
