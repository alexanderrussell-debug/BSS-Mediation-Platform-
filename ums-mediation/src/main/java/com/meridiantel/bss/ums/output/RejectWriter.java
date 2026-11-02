package com.meridiantel.bss.ums.output;

import java.io.IOException;

import com.meridiantel.bss.ums.core.RejectException;
import com.meridiantel.bss.ums.core.UsageRecord;

/**
 * Rejected records with reason. Revenue Assurance reviews these daily; R10 records are
 * recycled by copying the raw lines back into the input directory (manual, see runbook).
 */
public class RejectWriter extends OutputFile {

    public RejectWriter(String dir, String name) throws IOException {
        super(dir, name);
    }

    public void write(UsageRecord u, RejectException e) throws IOException {
        line(e.getReasonCode() + "|" + e.getMessage() + "|" + u.getSourceStream() + "|" + u.getSourceFile() + "|"
                + u.getSourcePosition() + "|" + u.getRawData());
        count++;
    }
}
