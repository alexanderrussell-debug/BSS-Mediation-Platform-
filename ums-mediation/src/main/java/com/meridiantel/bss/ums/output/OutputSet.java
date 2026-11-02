package com.meridiantel.bss.ums.output;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.util.Date;

import com.meridiantel.bss.ums.core.StreamConfig;
import com.meridiantel.bss.ums.util.DateUtil;

/**
 * All output files for one input file.
 */
public class OutputSet {

    public BillingUdrWriter billing;
    public DwhCsvWriter dwh;
    public FraudFeedWriter fraud;
    public OcsReconWriter ocs;
    public RejectWriter reject;

    public static OutputSet open(String dataDir, StreamConfig cfg) throws IOException {
        long seq = nextSeq(dataDir, cfg.getName());
        String ts = DateUtil.format(new Date(), "yyyyMMddHHmmss", cfg.getTimezone());
        String base = cfg.getName() + "_" + ts + "_" + seq;
        OutputSet o = new OutputSet();
        o.billing = new BillingUdrWriter(dataDir + "/output/billing", "UDR_" + base + ".dat", seq, cfg.getName(), cfg.getTimezone());
        o.dwh = new DwhCsvWriter(dataDir + "/output/dwh", "DWH_" + base + ".csv");
        o.fraud = new FraudFeedWriter(dataDir + "/output/fraud", "FMS_" + base + ".txt");
        o.ocs = new OcsReconWriter(dataDir + "/output/ocs", "OCS_" + base + ".txt");
        o.reject = new RejectWriter(dataDir + "/output/reject", "REJ_" + base + ".txt");
        return o;
    }

    public void closeAll() {
        billing.close();
        dwh.close();
        fraud.close();
        ocs.close();
        reject.close();
    }

    /** File sequence per stream, kept in state/seq_STREAM.txt (BillCore checks for gaps). */
    private static synchronized long nextSeq(String dataDir, String stream) throws IOException {
        File f = new File(dataDir + "/state/seq_" + stream + ".txt");
        f.getParentFile().mkdirs();
        long seq = 0;
        if (f.exists()) {
            BufferedReader r = new BufferedReader(new FileReader(f));
            String s = r.readLine();
            r.close();
            if (s != null) seq = Long.parseLong(s.trim());
        }
        seq++;
        FileWriter w = new FileWriter(f);
        w.write(String.valueOf(seq));
        w.close();
        return seq;
    }
}
