package com.meridiantel.bss.ums.util;

public class StringUtil {

    public static boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    /** Right-pad / truncate for fixed-width output. Truncates silently. */
    public static String rpad(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s.substring(0, len);
        StringBuffer sb = new StringBuffer(s);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }

    /** Left-pad numbers with zeros. Truncates from the LEFT if too long. */
    public static String lpad(long n, int len) {
        String s = String.valueOf(n);
        if (s.length() >= len) return s.substring(s.length() - len);
        StringBuffer sb = new StringBuffer();
        for (int i = s.length(); i < len; i++) sb.append('0');
        sb.append(s);
        return sb.toString();
    }

    public static String csv(String s) {
        if (s == null) return "";
        return s.replace(',', ' ');
    }
}
