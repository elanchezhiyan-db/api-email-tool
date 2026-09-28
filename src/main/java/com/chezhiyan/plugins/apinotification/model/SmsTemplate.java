package com.chezhiyan.plugins.apinotification.model;

public class SmsTemplate {

    private String id;
    private String templateName;
    private String message;

    public SmsTemplate() {
    }

    public SmsTemplate(String id, String templateName, String message) {
        this.id = id;
        this.templateName = templateName;
        this.message = message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
