package com.meridiantel.bss.ums.decode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import com.meridiantel.bss.ums.core.StreamConfig;
import com.meridiantel.bss.ums.core.UmsConstants;
import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.util.DateUtil;

/**
 * SMSC submit records, pipe delimited, no header:
 * <pre>
 * MSG_ID|ORIG_ADDR|DEST_ADDR|SUBMIT_TIME(yyyyMMddHHmmss)|STATUS|SMSC_ID|SEGMENTS
 * </pre>
 */
public class SmscPipeDecoder implements Decoder {

    public List decode(File file, StreamConfig cfg) throws IOException {
        List result = new ArrayList();
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            String line;
            int pos = 0;
            while ((line = br.readLine()) != null) {
                if (line.trim().length() == 0 || line.startsWith("#")) continue;
                pos++;
                UsageRecord u = new UsageRecord();
                u.setSourceStream(cfg.getName());
                u.setSourceFile(file.getName());
                u.setSourcePosition(pos);
                u.setRawData(line);
                // StringTokenizer skips empty tokens -> field shift when a field is empty (UMS-260)
                StringTokenizer st = new StringTokenizer(line, "|");
                if (st.countTokens() != 7) {
                    u.getExtra().put("DECODE_ERROR", "Expected 7 fields, got " + st.countTokens());
                    result.add(u);
                    continue;
                }
                try {
                    u.getExtra().put("MSG_ID", st.nextToken());
                    // SMSC exports addresses in international format
                    u.setServedMsisdn(st.nextToken());
                    u.setServedInternational(true);
                    u.setOtherParty(st.nextToken());
                    u.setOtherInternational(true);
                    u.setStartTime(DateUtil.parse(st.nextToken(), "yyyyMMddHHmmss", cfg.getTimezone()));
                    u.setSmsStatus(st.nextToken());
                    u.setNodeId(st.nextToken());
                    u.setSmsSegments(Integer.parseInt(st.nextToken()));
                    u.setRecordType(UmsConstants.RT_SMS_MO);
                    u.setDurationSec(0);
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
