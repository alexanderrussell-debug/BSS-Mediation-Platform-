import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.meridiantel.bss.ums.core.StreamConfig;
import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.decode.BerTlvReader;
import com.meridiantel.bss.ums.decode.MscBerDecoder;
import com.meridiantel.bss.ums.util.DateUtil;

/**
 * Runs the real UMS MSC_ID decode path (BerTlvReader.decodeTimestamp -> DateUtil.parse with the
 * stream timezone -> DateUtil.format for UDR local time / DWH UTC) for Jakarta (WIB) and
 * Makassar (WITA) switch timestamps. Java 7 source; classpath = UMS jar.
 *
 *   java -cp ums.jar:. MscIdTzCheck [sample-MSC_ID-file.ber ...]
 */
public class MscIdTzCheck {

    // local wall-clock + offset as a switch would write it, and the zone the switch is really in
    static final String[][] CASES = {
        { "260115093000", "+0700", "Asia/Jakarta",  "JKT winter weekday 09:30 WIB" },
        { "260115193000", "+0700", "Asia/Jakarta",  "JKT 19:30 WIB (peak/off-peak boundary 20h)" },
        { "260115200000", "+0800", "Asia/Makassar", "MKS 20:00 WITA (= 19:00 WIB, boundary)" },
        { "260115093000", "+0800", "Asia/Makassar", "MKS 09:30 WITA" },
        { "260701000030", "+0800", "Asia/Makassar", "MKS 00:00:30 WITA (day boundary)" },
        { "261231233000", "+0800", "Asia/Makassar", "MKS 23:30 WITA new-year eve" },
        { "150315120000", "+0700", "Asia/Jakarta",  "JKT 2015 (pre-JDK7u80 tzdata era)" },
    };

    public static void main(String[] args) throws Exception {
        System.out.println("# java.version=" + System.getProperty("java.version") + " default.tz=" + TimeZone.getDefault().getID()
                + " file.encoding=" + System.getProperty("file.encoding"));
        String streamTz = "Asia/Jakarta"; // ums.properties stream.MSC_ID.timezone (all ID MSCs)
        System.out.println("# stream.MSC_ID.timezone=" + streamTz);
        System.out.println("case|raw_bcd|switch_offset|true_zone|parsed_utc|true_utc|error_min|udr_local(" + streamTz
                + ")|dwh_utc|hourOfDay(stream)|hourOfDay(true)");
        for (int i = 0; i < CASES.length; i++) {
            String[] c = CASES[i];
            byte[] ts = bcd(c[0], c[1]);
            String decoded = BerTlvReader.decodeTimestamp(ts);
            Date parsed = DateUtil.parse(decoded, "yyMMddHHmmss", streamTz);
            Date truth = DateUtil.parse(decoded, "yyMMddHHmmss", c[2]);
            long errMin = (parsed.getTime() - truth.getTime()) / 60000L;
            System.out.println(c[3] + "|" + decoded + "|" + c[1] + "|" + c[2] + "|"
                    + DateUtil.format(parsed, "yyyy-MM-dd HH:mm:ss", "UTC") + "|"
                    + DateUtil.format(truth, "yyyy-MM-dd HH:mm:ss", "UTC") + "|" + errMin + "|"
                    + DateUtil.format(parsed, "yyyyMMddHHmmss", streamTz) + "|"
                    + DateUtil.format(parsed, "yyyy-MM-dd HH:mm:ss", "UTC") + "|"
                    + DateUtil.hourOfDay(parsed, streamTz) + "|" + DateUtil.hourOfDay(truth, c[2]));
        }

        StreamConfig cfg = new StreamConfig();
        cfg.setName("MSC_ID");
        cfg.setType("MSC_BER");
        cfg.setTimezone(streamTz);
        cfg.setCountry("ID");
        for (int a = 0; a < args.length; a++) {
            File f = new File(args[a]);
            System.out.println();
            System.out.println("# decode " + f.getName() + " via MscBerDecoder");
            System.out.println("pos|node|msisdn|startTime.getTime()|start_utc|udr_local(" + streamTz + ")|Date.toString()");
            List recs = new MscBerDecoder().decode(f, cfg);
            for (int i = 0; i < recs.size(); i++) {
                UsageRecord u = (UsageRecord) recs.get(i);
                Date d = u.getStartTime();
                System.out.println(u.getSourcePosition() + "|" + u.getNodeId() + "|" + u.getServedMsisdn() + "|"
                        + (d == null ? "null" : String.valueOf(d.getTime())) + "|"
                        + DateUtil.format(d, "yyyy-MM-dd HH:mm:ss", "UTC") + "|"
                        + DateUtil.format(d, "yyyyMMddHHmmss", streamTz) + "|" + d);
            }
        }
    }

    static byte[] bcd(String yyMMddHHmmss, String offset) {
        String s = yyMMddHHmmss + offset.substring(1);
        byte[] b = new byte[9];
        for (int i = 0; i < 6; i++) {
            b[i] = (byte) (((s.charAt(2 * i) - '0') << 4) | (s.charAt(2 * i + 1) - '0'));
        }
        b[6] = (byte) offset.charAt(0);
        b[7] = (byte) (((s.charAt(12) - '0') << 4) | (s.charAt(13) - '0'));
        b[8] = (byte) (((s.charAt(14) - '0') << 4) | (s.charAt(15) - '0'));
        return b;
    }
}
