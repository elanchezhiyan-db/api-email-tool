package com.chezhiyan.plugins.apinotification.model;

public class NotificationTemplate {

    private String id;
    private String templateName;
    private String subject;
    private String body;

    public NotificationTemplate() {
    }

    public NotificationTemplate(String id, String templateName, String subject, String body) {
        this.id = id;
        this.templateName = templateName;
        this.subject = subject;
        this.body = body;
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

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
