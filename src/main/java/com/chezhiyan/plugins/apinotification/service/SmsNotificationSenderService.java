package com.chezhiyan.plugins.apinotification.service;

public interface SmsNotificationSenderService {

    void send(
            String smsApiUrl,
            String recipients,
            String message,
            String templateName
    );
}
