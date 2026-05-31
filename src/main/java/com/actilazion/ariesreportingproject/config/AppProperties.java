package com.actilazion.ariesreportingproject.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.reporting")
public class AppProperties {
    private String exportDir = "/tmp/aries-exports";
    private int exportTtlHours = 24;
    private String mailFrom = "noreply@aries.local";
    private int cacheTtlMinutes = 10;

    public String getExportDir() { return exportDir; }
    public void setExportDir(String v) { this.exportDir = v; }

    public int getExportTtlHours() { return exportTtlHours; }
    public void setExportTtlHours(int v) { this.exportTtlHours = v; }

    public String getMailFrom() { return mailFrom; }
    public void setMailFrom(String v) { this.mailFrom = v; }

    public int  getCacheTtlMinutes() { return cacheTtlMinutes; }
    public void setCacheTtlMinutes(int v) { this.cacheTtlMinutes = v; }

}
