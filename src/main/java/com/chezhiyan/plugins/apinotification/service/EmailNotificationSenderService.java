package com.chezhiyan.plugins.apinotification.service;

import org.joget.apps.app.model.AppDefinition;
import org.joget.workflow.model.WorkflowAssignment;

public interface EmailNotificationSenderService {

    void send(
            WorkflowAssignment assignment,
            AppDefinition appDef,
            String apiUrl,
            String apiUrlWithAttachments,
            String to,
            String cc,
            String bcc,
            String subject,
            String body,
            String resolvedAttachmentsJson,
            String triggeredBy,
            String templateName,
            String channel
    );
}
