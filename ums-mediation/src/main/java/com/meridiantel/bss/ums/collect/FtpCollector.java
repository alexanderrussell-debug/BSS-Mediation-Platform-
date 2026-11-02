package com.meridiantel.bss.ums.collect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.log4j.Logger;

import com.meridiantel.bss.ums.core.StreamConfig;
import com.meridiantel.bss.ums.util.FileUtil;

/**
 * Pulls CDR files from the network element's FTP server and deletes them remotely.
 * (SFTP not supported by the 2012 MSCs; newer nodes support it but nobody changed this.)
 */
public class FtpCollector implements FileCollector {

    private static final Logger log = Logger.getLogger(FtpCollector.class);

    public List collect(StreamConfig cfg, String workDir) throws Exception {
        List result = new ArrayList();
        FileUtil.ensureDir(workDir);
        FTPClient ftp = new FTPClient();
        try {
            ftp.connect(cfg.getFtpHost(), cfg.getFtpPort());
            if (!ftp.login(cfg.getFtpUser(), cfg.getFtpPassword())) {
                throw new Exception("FTP login failed for " + cfg.getFtpUser() + "@" + cfg.getFtpHost());
            }
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            FTPFile[] files = ftp.listFiles(cfg.getFtpRemoteDir());
            for (int i = 0; i < files.length; i++) {
                String name = files[i].getName();
                if (!files[i].isFile() || !name.matches(cfg.getFilePattern())) continue;
                File local = new File(workDir, name);
                OutputStream out = new FileOutputStream(local);
                boolean ok = ftp.retrieveFile(cfg.getFtpRemoteDir() + "/" + name, out);
                out.close();
                // delete on the switch so it doesn't fill up
                ftp.deleteFile(cfg.getFtpRemoteDir() + "/" + name);
                if (ok) {
                    result.add(local);
                } else {
                    log.error("Download failed for " + name + " (" + ftp.getReplyString() + ")");
                }
            }
            ftp.logout();
        } finally {
            if (ftp.isConnected()) {
                try { ftp.disconnect(); } catch (Exception ignore) { }
            }
        }
        log.info(cfg.getName() + ": collected " + result.size() + " files via FTP from " + cfg.getFtpHost());
        return result;
    }
}
