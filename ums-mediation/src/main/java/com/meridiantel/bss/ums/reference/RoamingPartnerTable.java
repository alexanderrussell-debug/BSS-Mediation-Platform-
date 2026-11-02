package com.meridiantel.bss.ums.reference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Hashtable;

import org.apache.log4j.Logger;

/**
 * Serving network (MCC+MNC) -> roaming partner. HOME_FLAG=Y for Meridian's own networks.
 * Format: MCC_MNC,OPERATOR,COUNTRY,HOME_FLAG
 */
public class RoamingPartnerTable {

    private static final Logger log = Logger.getLogger(RoamingPartnerTable.class);

    private String file;
    private Hashtable partners = new Hashtable();
    private Hashtable home = new Hashtable();

    public void load() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.startsWith("MCC_MNC") || line.trim().length() == 0) continue;
                String[] f = line.split(",");
                partners.put(f[0].trim(), f[1].trim());
                if ("Y".equals(f[3].trim())) {
                    home.put(f[0].trim(), f[2].trim());
                }
            }
        } finally {
            br.close();
        }
        log.info("Loaded " + partners.size() + " PLMNs, " + home.size() + " home networks");
    }

    public boolean isHome(String mccMnc) {
        return mccMnc != null && home.containsKey(mccMnc);
    }

    public String partner(String mccMnc) {
        if (mccMnc == null) return "UNKNOWN";
        String p = (String) partners.get(mccMnc);
        return p == null ? "UNKNOWN" : p;
    }

    public void setFile(String file) { this.file = file; }
}
