package com.chezhiyan.plugins.apiemail.model;

public class ConfigurationTemplate {

    private String id;
    private String configurationName;
    private String to;
    private String cc;
    private String bcc;

    public ConfigurationTemplate() {
    }

    public ConfigurationTemplate(String id, String configurationName) {
        this.id = id;
        this.configurationName = configurationName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConfigurationName() {
        return configurationName;
    }

    public void setConfigurationName(String configurationName) {
        this.configurationName = configurationName;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getCc() {
        return cc;
    }

    public void setCc(String cc) {
        this.cc = cc;
    }

    public String getBcc() {
        return bcc;
    }

    public void setBcc(String bcc) {
        this.bcc = bcc;
    }
}
