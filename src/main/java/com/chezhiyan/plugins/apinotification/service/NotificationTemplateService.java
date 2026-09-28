package com.chezhiyan.plugins.apinotification.service;

import java.util.Collection;

import org.joget.apps.app.model.AppDefinition;

import com.chezhiyan.plugins.apinotification.model.NotificationTemplate;

public interface NotificationTemplateService {

    NotificationTemplate getTemplate(AppDefinition appDef, String formId, String templateId);

    Collection<NotificationTemplate> getTemplates(AppDefinition appDef, String formId);

    Collection<NotificationTemplate> getAllTemplateOptions(AppDefinition appDef);
}
