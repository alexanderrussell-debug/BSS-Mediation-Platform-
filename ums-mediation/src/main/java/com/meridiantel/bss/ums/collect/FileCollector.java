package com.meridiantel.bss.ums.collect;

import java.util.List;

import com.meridiantel.bss.ums.core.StreamConfig;

public interface FileCollector {

    /** Fetch new files into workDir and return them (List of java.io.File), oldest first. */
    List collect(StreamConfig cfg, String workDir) throws Exception;
}
