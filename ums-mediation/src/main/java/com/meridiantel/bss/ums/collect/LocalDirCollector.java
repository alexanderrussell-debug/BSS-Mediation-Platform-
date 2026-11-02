package com.meridiantel.bss.ums.collect;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;

import com.meridiantel.bss.ums.core.StreamConfig;
import com.meridiantel.bss.ums.util.FileUtil;

/**
 * Picks up files that the network pushes (or the legacy Perl script pulls) into the input dir.
 * No "file complete" check - relies on switches writing .tmp and renaming.
 */
public class LocalDirCollector implements FileCollector {

    private static final Logger log = Logger.getLogger(LocalDirCollector.class);

    public List collect(StreamConfig cfg, String workDir) {
        List result = new ArrayList();
        File in = new File(cfg.getInputDir());
        File[] files = in.listFiles();
        if (files == null) {
            log.warn("Input dir " + in + " does not exist");
            return result;
        }
        Arrays.sort(files);
        FileUtil.ensureDir(workDir);
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (!f.isFile() || !f.getName().matches(cfg.getFilePattern())) continue;
            File target = new File(workDir, f.getName());
            if (f.renameTo(target)) {
                result.add(target);
            } else {
                log.error("Could not move " + f + " to work dir");
            }
        }
        if (result.size() > 0) {
            log.info(cfg.getName() + ": collected " + result.size() + " files");
        }
        return result;
    }
}
