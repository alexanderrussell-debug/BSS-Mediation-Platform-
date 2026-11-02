package com.meridiantel.bss.ums.util;

import java.util.Hashtable;

/**
 * Copied from NetProv OSS (com.meridiantel.oss.netprov.util.MsisdnUtil) in 2016.
 * The two copies have drifted. Returns null if the number can't be normalised.
 */
public class MsisdnUtil {

    private static Hashtable countryCodes = new Hashtable();

    static {
        countryCodes.put("SG", "65");
        countryCodes.put("MY", "60");
        countryCodes.put("ID", "62");
        countryCodes.put("PH", "63");
        countryCodes.put("TH", "66");
        countryCodes.put("VN", "84");
    }

    public static String countryCode(String country) {
        return (String) countryCodes.get(country);
    }

    /**
     * @param international true if the number is already in international format (TON=international)
     */
    public static String normalize(String number, boolean international, String country) {
        if (number == null) return null;
        String n = number.trim();
        if (n.startsWith("+")) {
            n = n.substring(1);
            international = true;
        } else if (n.startsWith("00")) {
            n = n.substring(2);
            international = true;
        }
        for (int i = 0; i < n.length(); i++) {
            if (!Character.isDigit(n.charAt(i))) return null;
        }
        if (n.length() == 0) return null;
        if (international) return n;
        if (n.length() <= 6) return n; // short code / service number - leave as is
        String cc = countryCode(country);
        if (cc == null) return null;
        if (n.startsWith("0")) n = n.substring(1);
        if (n.startsWith(cc) && n.length() > 10) return n; // already has cc (some MSCs omit TON)
        return cc + n;
    }

    public static boolean isShortCode(String n) {
        return n != null && n.length() <= 6;
    }
}
