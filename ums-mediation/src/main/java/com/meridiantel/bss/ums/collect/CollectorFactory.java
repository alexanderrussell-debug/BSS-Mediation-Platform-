package com.meridiantel.bss.ums.collect;

public class CollectorFactory {

    public FileCollector getCollector(String type) {
        if ("FTP".equalsIgnoreCase(type)) {
            return new FtpCollector();
        }
        return new LocalDirCollector();
    }
}
