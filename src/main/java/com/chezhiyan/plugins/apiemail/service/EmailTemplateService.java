package com.chezhiyan.plugins.apiemail.service;

import java.util.Collection;

import org.joget.apps.app.model.AppDefinition;

import com.chezhiyan.plugins.apiemail.model.EmailTemplate;

public interface EmailTemplateService {

    EmailTemplate getTemplate(AppDefinition appDef, String formId, String templateId);

    Collection<EmailTemplate> getTemplates(AppDefinition appDef, String formId);
}
