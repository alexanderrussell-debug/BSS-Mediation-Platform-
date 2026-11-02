package com.meridiantel.bss.ums.audit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.log4j.Logger;

import com.meridiantel.bss.ums.core.ControlTotals;
import com.meridiantel.bss.ums.util.DateUtil;

/**
 * Control totals per input file, one CSV per day. Revenue Assurance loads this into their RA tool.
 */
public class AuditLogger {

    private static final Logger log = Logger.getLogger(AuditLogger.class);

    private String auditDir;

    public synchronized void write(ControlTotals ct) {
        File dir = new File(auditDir);
        dir.mkdirs();
        File f = new File(dir, "control_totals_" + DateUtil.today() + ".csv");
        boolean newFile = !f.exists();
        try {
            FileWriter w = new FileWriter(f, true);
            if (newFile) {
                w.write("STREAM,FILE,INPUT,REJECTED,DUPLICATES,FILTERED,HELD,WRITTEN,FRAUD,DURATION_SEC,VOLUME_BYTES,ELAPSED_MS,BALANCED,STATUS\n");
            }
            w.write(ct.stream + "," + ct.fileName + "," + ct.input + "," + ct.rejected + "," + ct.duplicates + "," + ct.filtered
                    + "," + ct.held + "," + ct.written + "," + ct.fraud + "," + ct.totalDurationSec + "," + ct.totalVolumeBytes
                    + "," + (ct.endMs - ct.startMs) + "," + (ct.isBalanced() ? "Y" : "N") + "," + ct.status + "\n");
            w.close();
        } catch (IOException e) {
            log.error("Could not write control totals: " + ct, e);
        }
    }

    public void setAuditDir(String auditDir) { this.auditDir = auditDir; }
}
