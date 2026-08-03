package com.chezhiyan.plugins.apiemail.service;

import org.joget.apps.app.model.AppDefinition;
import org.joget.workflow.model.WorkflowAssignment;

public interface ApiEmailSenderService {

    void send(
            WorkflowAssignment assignment,
            AppDefinition appDef,
            String apiUrl,
            String apiKey,
            String from,
            String to,
            String cc,
            String bcc,
            String subject,
            String body,
            String resolvedAttachmentsJson
    );
}
