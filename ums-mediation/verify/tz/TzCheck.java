import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dumps the JDK's tzdata view of the zones UMS uses, so the output of two JDKs can be diffed.
 * Java 7 source level on purpose (must compile with javac 7 and run on 7 and 21).
 *
 *   java TzCheck [fromYear toYear]      (default 1900..2040)
 *
 * Prints: tzdata version, per zone raw offset / DST flag / display names, every UTC-offset
 * transition found in the range (located to the minute), and offsets at CDR-relevant instants.
 */
public class TzCheck {

    static final String[] ZONES = {
        "Asia/Jakarta", "Asia/Makassar", "Asia/Ujung_Pandang", "Asia/Pontianak", "Asia/Jayapura",
        "Asia/Singapore", "Asia/Kuala_Lumpur", "Asia/Manila", "UTC"
    };

    public static void main(String[] args) throws Exception {
        int from = args.length > 0 ? Integer.parseInt(args[0]) : 1900;
        int to = args.length > 1 ? Integer.parseInt(args[1]) : 2040;
        System.out.println("# java.version=" + System.getProperty("java.version"));
        System.out.println("# java.vendor=" + System.getProperty("java.vendor"));
        System.out.println("# tzdata=" + tzdataVersion());
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int z = 0; z < ZONES.length; z++) {
            TimeZone tz = TimeZone.getTimeZone(ZONES[z]);
            System.out.println();
            System.out.println("== " + ZONES[z] + " resolvedId=" + tz.getID() + " rawOffset=" + fmt(tz.getRawOffset())
                    + " useDaylightTime=" + tz.useDaylightTime()
                    + " short=" + tz.getDisplayName(false, TimeZone.SHORT, Locale.US)
                    + " long=" + tz.getDisplayName(false, TimeZone.LONG, Locale.US));
            long t = utcMillis(from);
            long end = utcMillis(to + 1);
            long step = 6L * 3600 * 1000;
            int prev = tz.getOffset(t);
            System.out.println("  " + utc.format(new Date(t)) + "Z start offset " + fmt(prev));
            while (t < end) {
                long n = t + step;
                int o = tz.getOffset(n);
                if (o != prev) {
                    long lo = t, hi = n; // offset(lo)=prev, offset(hi)=o
                    while (hi - lo > 60000L) {
                        long mid = lo + (hi - lo) / 2;
                        if (tz.getOffset(mid) == prev) lo = mid; else hi = mid;
                    }
                    System.out.println("  " + utc.format(new Date(hi)) + "Z " + fmt(prev) + " -> " + fmt(o));
                    prev = o;
                }
                t = n;
            }
        }
        System.out.println();
        System.out.println("== offsets at CDR-relevant instants (UTC noon on the 1st of each quarter 2010..2030)");
        for (int y = 2010; y <= 2030; y++) {
            for (int m = 0; m < 12; m += 3) {
                long t = utcMillis(y) ; // Jan 1
                java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                c.clear();
                c.set(y, m, 1, 12, 0, 0);
                t = c.getTimeInMillis();
                StringBuilder sb = new StringBuilder(utc.format(new Date(t)) + "Z");
                for (int z = 0; z < ZONES.length; z++) {
                    sb.append(" ").append(ZONES[z]).append("=").append(fmt(TimeZone.getTimeZone(ZONES[z]).getOffset(t)));
                }
                System.out.println(sb);
            }
        }
    }

    static long utcMillis(int year) {
        java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year, 0, 1, 0, 0, 0);
        return c.getTimeInMillis();
    }

    static String fmt(int ms) {
        int s = ms / 1000;
        char sign = s < 0 ? '-' : '+';
        s = Math.abs(s);
        String r = sign + two(s / 3600) + ":" + two((s / 60) % 60);
        if (s % 60 != 0) r += ":" + two(s % 60);
        return r;
    }

    static String two(int i) { return i < 10 ? "0" + i : String.valueOf(i); }

    static String tzdataVersion() {
        try {
            Class c = Class.forName("sun.util.calendar.ZoneInfoFile");
            Object v = c.getMethod("getVersion").invoke(null);
            if (v != null) return v + " (ZoneInfoFile.getVersion)";
        } catch (Throwable ignore) {
        }
        String home = System.getProperty("java.home");
        String[] files = { home + "/lib/tzdb.dat", home + "/lib/zi/ZoneInfoMappings" };
        for (int i = 0; i < files.length; i++) {
            File f = new File(files[i]);
            if (!f.exists()) continue;
            try {
                byte[] b = new byte[4096];
                FileInputStream in = new FileInputStream(f);
                int n = in.read(b);
                in.close();
                Matcher m = Pattern.compile("(?:tzdata)?(\\d{4}[a-z])").matcher(new String(b, 0, Math.max(n, 0), "ISO-8859-1"));
                if (m.find()) return m.group(1) + " (" + f.getName() + ")";
            } catch (Exception ignore) {
            }
        }
        return "unknown";
    }
}
