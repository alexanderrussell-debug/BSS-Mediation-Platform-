package com.meridiantel.bss.ums.output;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.log4j.Logger;

/**
 * Base for all output files. Written as NAME.tmp, renamed on close so downstream
 * pollers never pick up half-written files. Empty files are deleted on close.
 */
public abstract class OutputFile {

    protected final Logger log = Logger.getLogger(getClass());

    protected File tmp;
    protected File target;
    protected BufferedWriter w;
    protected long count = 0;

    protected OutputFile(String dir, String name) throws IOException {
        new File(dir).mkdirs();
        this.target = new File(dir, name);
        this.tmp = new File(dir, name + ".tmp");
        this.w = new BufferedWriter(new FileWriter(tmp));
        writeHeader();
    }

    protected void writeHeader() throws IOException {
    }

    protected void writeTrailer() throws IOException {
    }

    protected void line(String s) throws IOException {
        w.write(s);
        w.write("\n");
    }

    public long getCount() {
        return count;
    }

    public File getTarget() {
        return target;
    }

    public void close() {
        try {
            writeTrailer();
            w.close();
            if (count == 0) {
                tmp.delete();
                return;
            }
            if (!tmp.renameTo(target)) {
                log.error("Could not rename " + tmp + " to " + target);
            } else {
                log.info("Wrote " + count + " records to " + target.getName());
            }
        } catch (IOException e) {
            log.error("Error closing " + tmp, e);
        }
    }
}
