package com.meridiantel.bss.ums.util;

import java.io.File;

import org.apache.log4j.Logger;

public class FileUtil {

    private static final Logger log = Logger.getLogger(FileUtil.class);

    public static void ensureDir(String path) {
        File d = new File(path);
        if (!d.exists()) {
            d.mkdirs();
        }
    }

    /**
     * Move processed input file to archive and gzip it.
     * Uses system gzip because java.util.zip was "too slow" on Solaris (2012).
     */
    public static void archive(File f, String archiveDir) {
        ensureDir(archiveDir);
        File target = new File(archiveDir, f.getName());
        if (!f.renameTo(target)) {
            log.warn("Could not move " + f + " to archive");
            return;
        }
        try {
            Process p = Runtime.getRuntime().exec(new String[] { "sh", "-c", "gzip -f " + target.getAbsolutePath() });
            int rc = p.waitFor();
            if (rc != 0) {
                log.warn("gzip returned " + rc + " for " + target);
            }
        } catch (Exception e) {
            log.warn("gzip failed for " + target + ": " + e.getMessage());
        }
    }

    public static boolean moveTo(File f, String dir) {
        ensureDir(dir);
        return f.renameTo(new File(dir, f.getName()));
    }
}
