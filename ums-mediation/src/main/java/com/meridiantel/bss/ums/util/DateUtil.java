package com.meridiantel.bss.ums.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Date parsing/formatting with a format cache "for performance" (UMS-188, 2013:
 * creating SimpleDateFormat per record was 8% of CPU on the old SPARC boxes).
 */
public class DateUtil {

    private static Map formatCache = new HashMap();

    private static SimpleDateFormat getFormat(String pattern, String tz) {
        SimpleDateFormat f = (SimpleDateFormat) formatCache.get(pattern);
        if (f == null) {
            f = new SimpleDateFormat(pattern);
            f.setLenient(false);
            formatCache.put(pattern, f);
        }
        f.setTimeZone(TimeZone.getTimeZone(tz));
        return f;
    }

    public static Date parse(String s, String pattern, String tz) throws ParseException {
        return getFormat(pattern, tz).parse(s);
    }

    public static String format(Date d, String pattern, String tz) {
        if (d == null) return "";
        return getFormat(pattern, tz).format(d);
    }

    public static int hourOfDay(Date d, String tz) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(tz));
        c.setTime(d);
        return c.get(Calendar.HOUR_OF_DAY);
    }

    public static String today() {
        return new SimpleDateFormat("yyyyMMdd").format(new Date());
    }
}
