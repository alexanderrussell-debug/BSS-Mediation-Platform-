package com.meridiantel.bss.ums.core;

/**
 * One input stream (one network source). Loaded from ums.properties: stream.&lt;NAME&gt;.*
 */
public class StreamConfig {

    private String name;
    private String type;
    private String collector;   // LOCAL or FTP
    private String inputDir;
    private String filePattern;
    private String timezone;
    private String country;
    private String ftpHost;
    private int ftpPort = 21;
    private String ftpUser;
    private String ftpPassword;
    private String ftpRemoteDir;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCollector() { return collector; }
    public void setCollector(String collector) { this.collector = collector; }
    public String getInputDir() { return inputDir; }
    public void setInputDir(String inputDir) { this.inputDir = inputDir; }
    public String getFilePattern() { return filePattern; }
    public void setFilePattern(String filePattern) { this.filePattern = filePattern; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getFtpHost() { return ftpHost; }
    public void setFtpHost(String ftpHost) { this.ftpHost = ftpHost; }
    public int getFtpPort() { return ftpPort; }
    public void setFtpPort(int ftpPort) { this.ftpPort = ftpPort; }
    public String getFtpUser() { return ftpUser; }
    public void setFtpUser(String ftpUser) { this.ftpUser = ftpUser; }
    public String getFtpPassword() { return ftpPassword; }
    public void setFtpPassword(String ftpPassword) { this.ftpPassword = ftpPassword; }
    public String getFtpRemoteDir() { return ftpRemoteDir; }
    public void setFtpRemoteDir(String ftpRemoteDir) { this.ftpRemoteDir = ftpRemoteDir; }

    public String toString() {
        return "Stream[" + name + " " + type + " " + collector + " " + inputDir + " tz=" + timezone + "]";
    }
}
