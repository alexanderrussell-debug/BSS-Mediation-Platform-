package com.meridiantel.bss.ums.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Generates sample network files for dev/test (dated yesterday, so they pass the 90-day check).
 *
 *   java -cp ums.jar com.meridiantel.bss.ums.tools.SampleCdrGenerator ./data
 *
 * Writes into DATA/input/MSC_SG, MSC_ID, PGW_SG, SMSC_SG.
 */
public class SampleCdrGenerator {

    private static final long MB = 1024L * 1024L;

    private String dataDir;
    private Calendar base;

    public SampleCdrGenerator(String dataDir) {
        this.dataDir = dataDir;
        base = Calendar.getInstance(TimeZone.getTimeZone("Asia/Singapore"));
        base.add(Calendar.DAY_OF_MONTH, -1);
        base.set(Calendar.HOUR_OF_DAY, 10);
        base.set(Calendar.MINUTE, 0);
        base.set(Calendar.SECOND, 0);
        base.set(Calendar.MILLISECOND, 0);
    }

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "./data";
        new SampleCdrGenerator(dir).generateAll();
        System.out.println("Sample files written under " + dir + "/input");
    }

    public void generateAll() throws IOException {
        mscSg();
        mscId();
        pgwSg();
        smscSg();
    }

    private Date at(int minutes) {
        Calendar c = (Calendar) base.clone();
        c.add(Calendar.MINUTE, minutes);
        return c.getTime();
    }

    private String stamp() {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmm");
        f.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"));
        return f.format(base.getTime());
    }

    private File out(String stream, String name) {
        File d = new File(dataDir + "/input/" + stream);
        d.mkdirs();
        return new File(d, name);
    }

    // ================================================================== MSC (BER)

    private static final int INTL = 0x91;
    private static final int NATL = 0x81;

    private void mscSg() throws IOException {
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        String tz = "Asia/Singapore";
        String msc = "MSC-SG-01";
        String home = "52577";
        // 1 local mobile call (national dialled)
        f.write(moc(0, "6591000001", NATL, "91234567", at(1), 120, msc, home, tz, "+0800"));
        // 2 premium-rate 1900 call, 15 min
        f.write(moc(0, "6591000002", NATL, "19001112222", at(2), 900, msc, home, tz, "+0800"));
        // 3 IDD to UK
        byte[] idd = moc(0, "6591000003", INTL, "442071234567", at(3), 300, msc, home, tz, "+0800");
        f.write(idd);
        // 4 satellite, 20 min (fraud)
        f.write(moc(0, "6591000004", INTL, "8821612345678", at(4), 1200, msc, home, tz, "+0800"));
        // 5 MT at home (not billed)
        f.write(moc(1, "6591000001", INTL, "6598765432", at(5), 60, msc, home, tz, "+0800"));
        // 6 roaming in Korea, long call home (fraud)
        f.write(moc(0, "6591000005", INTL, "6567771234", at(6), 4000, "MSC-SG-01", "45011", tz, "+0800"));
        // 7 unanswered
        f.write(moc(0, "6591000006", NATL, "61234567", at(7), 0, msc, home, tz, "+0800"));
        // 8 test SIM
        f.write(moc(0, "6590000001", NATL, "91110000", at(8), 30, msc, home, tz, "+0800"));
        // 9 unknown subscriber (activated today, not in extract)
        f.write(moc(0, "6591999999", NATL, "91234567", at(9), 75, msc, home, tz, "+0800"));
        // 10 duplicate of #3 (switch resent block)
        f.write(idd);
        // 11 prepaid
        f.write(moc(0, "6581000001", NATL, "91234500", at(11), 45, msc, home, tz, "+0800"));
        // 12 bad record type
        f.write(moc(9, "6591000008", NATL, "91234567", at(12), 10, msc, home, tz, "+0800"));
        // 13 short code
        f.write(moc(0, "6591000009", NATL, "1688", at(13), 40, msc, home, tz, "+0800"));
        // 14 MT while roaming in Korea (billed)
        f.write(moc(1, "6591000005", INTL, "6591000001", at(14), 180, msc, "45011", tz, "+0800"));
        // 15 call forward to Malaysia
        f.write(moc(2, "6591000006", INTL, "60123456789", at(15), 200, msc, home, tz, "+0800"));
        // 16 toll-free 1800
        f.write(moc(0, "6591000003", NATL, "18002223333", at(16), 300, msc, home, tz, "+0800"));
        // block padding
        f.write(new byte[16]);
        write(out("MSC_SG", "CDR_MSC_SG01_" + stamp() + "_0001.ber"), f.toByteArray());
    }

    private void mscId() throws IOException {
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        String tz = "Asia/Jakarta";
        // Jakarta switch (WIB, +0700)
        f.write(moc(0, "6281200000001", INTL, "6281311112222", at(20), 240, "MSC-ID-JKT01", "51077", "Asia/Jakarta", "+0700"));
        f.write(moc(0, "6281200000002", NATL, "0215551234", at(21), 95, "MSC-ID-JKT01", "51077", "Asia/Jakarta", "+0700"));
        f.write(moc(0, "6281200000003", INTL, "6591000001", at(22), 600, "MSC-ID-JKT01", "51077", "Asia/Jakarta", "+0700"));
        // Makassar switch (WITA, +0800) - same stream
        f.write(moc(0, "6281300000004", NATL, "081200000001", at(23), 130, "MSC-ID-MKS01", "51077", "Asia/Makassar", "+0800"));
        f.write(moc(0, "6281300000005", NATL, "081200000002", at(24), 50, "MSC-ID-MKS01", "51077", "Asia/Makassar", "+0800"));
        write(out("MSC_ID", "CDR_MSC_ID01_" + stamp() + "_0001.ber"), f.toByteArray());
    }

    private byte[] moc(int type, String served, int otherTon, String other, Date answer, long duration, String msc,
                       String plmn, String tz, String offset) throws IOException {
        ByteArrayOutputStream r = new ByteArrayOutputStream();
        r.write(tlv(0x80, integer(type)));
        r.write(tlv(0x81, address(INTL, served)));
        if (type == 1) {
            r.write(tlv(0x83, address(otherTon, other)));
        } else {
            r.write(tlv(0x82, address(otherTon, other)));
        }
        r.write(tlv(0x84, timestamp(answer, tz, offset)));
        r.write(tlv(0x85, integer(duration)));
        r.write(tlv(0x86, msc.getBytes("ISO-8859-1")));
        r.write(tlv(0x87, plmn.getBytes("ISO-8859-1")));
        r.write(tlv(0x88, integer(0)));
        r.write(tlv(0x89, integer(seq++)));
        return tlv(0xA0, r.toByteArray());
    }

    private int seq = 1;

    private static byte[] tlv(int tag, byte[] v) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        if (v.length < 128) {
            o.write(v.length);
        } else if (v.length < 256) {
            o.write(0x81);
            o.write(v.length);
        } else {
            o.write(0x82);
            o.write(v.length >> 8);
            o.write(v.length & 0xFF);
        }
        o.write(v, 0, v.length);
        return o.toByteArray();
    }

    private static byte[] integer(long n) {
        if (n == 0) return new byte[] { 0 };
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int bytes = 0;
        long t = n;
        while (t != 0) { bytes++; t >>>= 8; }
        for (int i = bytes - 1; i >= 0; i--) o.write((int) (n >> (8 * i)) & 0xFF);
        byte[] b = o.toByteArray();
        if ((b[0] & 0x80) != 0) { // keep positive
            byte[] p = new byte[b.length + 1];
            System.arraycopy(b, 0, p, 1, b.length);
            return p;
        }
        return b;
    }

    private static byte[] address(int ton, String digits) {
        byte[] b = new byte[1 + (digits.length() + 1) / 2];
        b[0] = (byte) ton;
        for (int i = 0; i < digits.length(); i += 2) {
            int lo = digits.charAt(i) - '0';
            int hi = i + 1 < digits.length() ? digits.charAt(i + 1) - '0' : 0x0F;
            b[1 + i / 2] = (byte) ((hi << 4) | lo);
        }
        return b;
    }

    private static byte[] timestamp(Date d, String tz, String offset) {
        SimpleDateFormat f = new SimpleDateFormat("yyMMddHHmmss");
        f.setTimeZone(TimeZone.getTimeZone(tz));
        String s = f.format(d) + offset.substring(1);
        byte[] b = new byte[9];
        for (int i = 0; i < 6; i++) {
            b[i] = (byte) (((s.charAt(2 * i) - '0') << 4) | (s.charAt(2 * i + 1) - '0'));
        }
        b[6] = (byte) offset.charAt(0);
        b[7] = (byte) (((s.charAt(12) - '0') << 4) | (s.charAt(13) - '0'));
        b[8] = (byte) (((s.charAt(14) - '0') << 4) | (s.charAt(15) - '0'));
        return b;
    }

    // ================================================================== PGW (CSV)

    private static final String PGW_HEADER =
            "RECORD_TYPE,CHARGING_ID,SEQ_NO,SERVED_IMSI,SERVED_MSISDN,APN,START_TIME,DURATION,UPLINK_BYTES,DOWNLINK_BYTES,CAUSE,SERVING_PLMN,RAT";

    private void pgwSg() throws IOException {
        StringBuffer f1 = new StringBuffer(PGW_HEADER + "\n");
        // A: normal 50 MB session
        String a = pgw(10001, 1, "525770000000001", "6591000001", at(30), 1800, 5 * MB, 45 * MB, 0, "52577");
        f1.append(a);
        // B: heavy user, 3 partials (volume limit) totalling ~2.4 GB
        f1.append(pgw(10002, 1, "525770000000007", "6591000007", at(31), 3600, 100 * MB, 800 * MB, 16, "52577"));
        f1.append(pgw(10002, 2, "525770000000007", "6591000007", at(91), 3600, 100 * MB, 800 * MB, 16, "52577"));
        f1.append(pgw(10002, 3, "525770000000007", "6591000007", at(151), 2400, 50 * MB, 550 * MB, 0, "52577"));
        // C: first partial here, final in the next file
        f1.append(pgw(10003, 1, "525770000000008", "6591000008", at(32), 3600, 20 * MB, 180 * MB, 17, "52577"));
        // D: roaming in Korea, 600 MB (fraud feed)
        f1.append(pgw(10004, 1, "525770000000005", "6591000005", at(33), 5400, 60 * MB, 540 * MB, 0, "45011"));
        // prepaid data
        f1.append(pgw(10005, 1, "525770000000102", "6581000002", at(34), 600, 2 * MB, 18 * MB, 0, "52577"));
        // malformed (12 fields)
        f1.append("PGW,10006,1,525770000000009,6591000009,internet,BROKEN,60,100,200,0\n");
        // duplicate of A
        f1.append(a);
        // orphan partial - final never arrives (serving node change, final lost on CG failover)
        f1.append(pgw(10009, 1, "525770000000006", "6591000006", at(35), 1200, 10 * MB, 90 * MB, 18, "52577"));
        write(out("PGW_SG", "PGW_SG01_" + stamp() + "_0001.csv"), f1.toString().getBytes("ISO-8859-1"));

        StringBuffer f2 = new StringBuffer(PGW_HEADER + "\n");
        f2.append(pgw(10003, 2, "525770000000008", "6591000008", at(92), 1500, 10 * MB, 90 * MB, 0, "52577"));
        f2.append(pgw(10010, 1, "525770000000003", "6591000003", at(93), 900, 3 * MB, 27 * MB, 0, "52577"));
        write(out("PGW_SG", "PGW_SG01_" + stamp() + "_0002.csv"), f2.toString().getBytes("ISO-8859-1"));
    }

    private String pgw(long cid, int seqNo, String imsi, String msisdn, Date start, long dur, long up, long down, int cause, String plmn) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        f.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"));
        return "PGW," + cid + "," + seqNo + "," + imsi + "," + msisdn + ",internet," + f.format(start) + "," + dur + ","
                + up + "," + down + "," + cause + "," + plmn + ",NR\n";
    }

    // ================================================================== SMSC (pipe)

    private void smscSg() throws IOException {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss");
        f.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"));
        String t1 = f.format(at(40));
        String t2 = f.format(at(41));
        StringBuffer s = new StringBuffer();
        s.append("# SMSC01 export\n");
        s.append("M0001|6591000001|6591000002|" + t1 + "|DELIVERED|SMSC01|1\n");
        // two different messages in the same second to the same person
        s.append("M0002|6591000003|6591000004|" + t2 + "|DELIVERED|SMSC01|1\n");
        s.append("M0003|6591000003|6591000004|" + t2 + "|DELIVERED|SMSC01|1\n");
        // failed delivery
        s.append("M0004|6591000006|6591000007|" + t1 + "|FAILED|SMSC01|1\n");
        // true duplicate (same message id resent)
        s.append("M0001|6591000001|6591000002|" + t1 + "|DELIVERED|SMSC01|1\n");
        // empty destination
        s.append("M0005|6591000008||" + t1 + "|DELIVERED|SMSC01|1\n");
        // to a short code
        s.append("M0006|6591000009|72555|" + t1 + "|DELIVERED|SMSC01|1\n");
        // prepaid
        s.append("M0007|6581000003|6591000001|" + t1 + "|DELIVERED|SMSC01|1\n");
        // international to Philippines, 2 segments
        s.append("M0008|6591000005|639171234567|" + t1 + "|DELIVERED|SMSC01|2\n");
        write(out("SMSC_SG", "SMSC_SG01_" + stamp() + "_0001.txt"), s.toString().getBytes("ISO-8859-1"));
    }

    private static void write(File f, byte[] data) throws IOException {
        FileOutputStream o = new FileOutputStream(f);
        o.write(data);
        o.close();
    }
}
