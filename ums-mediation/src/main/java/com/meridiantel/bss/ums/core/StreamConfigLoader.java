package com.meridiantel.bss.ums.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

/**
 * Reads stream definitions:
 * <pre>
 *   ums.streams=MSC_SG,PGW_SG
 *   stream.MSC_SG.type=MSC_BER
 *   stream.MSC_SG.collector=LOCAL
 *   ...
 * </pre>
 */
public class StreamConfigLoader {

    private static final Logger log = Logger.getLogger(StreamConfigLoader.class);

    private Properties properties;
    private String dataDir;

    public List load() {
        List result = new ArrayList();
        String names = properties.getProperty("ums.streams", "");
        StringTokenizer st = new StringTokenizer(names, ",");
        while (st.hasMoreTokens()) {
            String n = st.nextToken().trim();
            String p = "stream." + n + ".";
            StreamConfig c = new StreamConfig();
            c.setName(n);
            c.setType(properties.getProperty(p + "type"));
            c.setCollector(properties.getProperty(p + "collector", "LOCAL"));
            c.setInputDir(properties.getProperty(p + "inputDir", dataDir + "/input/" + n));
            c.setFilePattern(properties.getProperty(p + "filePattern", ".*"));
            c.setTimezone(properties.getProperty(p + "timezone", "Asia/Singapore"));
            c.setCountry(properties.getProperty(p + "country", "SG"));
            c.setFtpHost(properties.getProperty(p + "ftp.host"));
            c.setFtpPort(Integer.parseInt(properties.getProperty(p + "ftp.port", "21")));
            c.setFtpUser(properties.getProperty(p + "ftp.user"));
            c.setFtpPassword(properties.getProperty(p + "ftp.password"));
            c.setFtpRemoteDir(properties.getProperty(p + "ftp.remoteDir"));
            log.info("Configured " + c);
            result.add(c);
        }
        return result;
    }

    public void setProperties(Properties properties) { this.properties = properties; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
}
