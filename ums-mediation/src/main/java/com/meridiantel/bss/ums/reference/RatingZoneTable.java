package com.meridiantel.bss.ums.reference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Destination prefix -> rating zone. Maintained by the BSS config team in refdata/rating_zones.csv.
 *
 * Lookup walks the number from the shortest prefix to the longest and returns the
 * first hit. The prefix table is designed so that country codes never overlap
 * (agreed with BSS config team, 2012).
 */
public class RatingZoneTable {

    private static final Logger log = Logger.getLogger(RatingZoneTable.class);

    private String file;
    private Map prefixes = new HashMap();
    private int maxLen = 0;

    public void load() throws IOException {
        Map tmp = new HashMap();
        int max = 0;
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.startsWith("PREFIX") || line.trim().length() == 0) continue;
                String[] f = line.split(",");
                String prefix = f[0].trim();
                tmp.put(prefix, f[1].trim());
                if (prefix.length() > max) max = prefix.length();
            }
        } finally {
            br.close();
        }
        prefixes = tmp;
        maxLen = max;
        log.info("Loaded " + prefixes.size() + " rating prefixes (max length " + maxLen + ")");
    }

    /** @return zone or null */
    public String lookup(String number) {
        if (number == null) return null;
        int limit = Math.min(maxLen, number.length());
        for (int len = 1; len <= limit; len++) {
            String z = (String) prefixes.get(number.substring(0, len));
            if (z != null) {
                return z;
            }
        }
        return null;
    }

    public void setFile(String file) { this.file = file; }
}
