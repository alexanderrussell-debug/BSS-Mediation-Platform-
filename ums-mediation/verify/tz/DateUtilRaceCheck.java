import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import com.meridiantel.bss.ums.util.DateUtil;

/**
 * DateUtil caches one SimpleDateFormat per pattern and calls setTimeZone() on it per call, while
 * MediationEngine runs every stream in its own thread. This hammers DateUtil from 4 threads the way
 * MSC_SG (Asia/Singapore) and MSC_ID (Asia/Jakarta) do and counts wrong results / exceptions.
 * Diagnostic only - documents existing behaviour (UMS-433 / RA-2025-007 candidates), not a fix.
 *
 *   java -cp ums.jar:. DateUtilRaceCheck [iterationsPerThread]
 */
public class DateUtilRaceCheck {

    public static void main(String[] args) throws Exception {
        final int n = args.length > 0 ? Integer.parseInt(args[0]) : 200000;
        final String raw = "260115093000";
        final long sg = DateUtil.parse(raw, "yyMMddHHmmss", "Asia/Singapore").getTime();
        final long id = DateUtil.parse(raw, "yyMMddHHmmss", "Asia/Jakarta").getTime();
        final AtomicLong wrongTz = new AtomicLong();
        final AtomicLong garbage = new AtomicLong();
        final AtomicLong exceptions = new AtomicLong();
        Thread[] t = new Thread[4];
        for (int k = 0; k < t.length; k++) {
            final String tz = k % 2 == 0 ? "Asia/Singapore" : "Asia/Jakarta";
            final long expect = k % 2 == 0 ? sg : id;
            final long other = k % 2 == 0 ? id : sg;
            t[k] = new Thread(new Runnable() {
                public void run() {
                    for (int i = 0; i < n; i++) {
                        try {
                            Date d = DateUtil.parse(raw, "yyMMddHHmmss", tz);
                            if (d.getTime() == other) wrongTz.incrementAndGet();
                            else if (d.getTime() != expect) garbage.incrementAndGet();
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                        }
                    }
                }
            });
        }
        for (int k = 0; k < t.length; k++) t[k].start();
        for (int k = 0; k < t.length; k++) t[k].join();
        System.out.println("java.version=" + System.getProperty("java.version") + " threads=4 parsesPerThread=" + n
                + " wrongTimezone(+/-1h)=" + wrongTz + " garbageDate=" + garbage + " exceptions=" + exceptions);
    }
}
