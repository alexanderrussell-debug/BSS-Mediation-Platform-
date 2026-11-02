package com.meridiantel.bss.ums.reference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Hashtable;

import org.apache.log4j.Logger;

/**
 * Subscriber reference data from the nightly BSS extract (SUBSCRIBER_EXTRACT_yyyymmdd.csv,
 * copied to refdata/subscribers.csv by cron at 02:00).
 *
 * Loaded ONCE at startup. New activations during the day are unknown until the
 * nightly restart -> their usage is rejected R10 and must be recycled manually (UMS-301).
 *
 * Format: MSISDN,ACCOUNT_NO,RATE_PLAN,PAYMENT_TYPE,STATUS
 */
public class SubscriberCache {

    private static final Logger log = Logger.getLogger(SubscriberCache.class);

    private String file;
    private Hashtable subscribers = new Hashtable();

    public void load() throws IOException {
        Hashtable tmp = new Hashtable();
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.startsWith("MSISDN") || line.trim().length() == 0) continue;
                String[] f = line.split(",");
                if (f.length < 5) {
                    log.warn("Bad subscriber line: " + line);
                    continue;
                }
                tmp.put(f[0].trim(), new String[] { f[1].trim(), f[2].trim(), f[3].trim(), f[4].trim() });
            }
        } finally {
            br.close();
        }
        subscribers = tmp;
        log.info("Loaded " + subscribers.size() + " subscribers from " + file);
    }

    /** @return {accountNo, ratePlan, paymentType, status} or null */
    public String[] lookup(String msisdn) {
        if (msisdn == null) return null;
        return (String[]) subscribers.get(msisdn);
    }

    public int size() {
        return subscribers.size();
    }

    public void setFile(String file) { this.file = file; }
}
