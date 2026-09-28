package com.chezhiyan.plugins.apinotification.service;

import java.util.Collection;

import org.joget.apps.app.model.AppDefinition;

import com.chezhiyan.plugins.apinotification.model.SmsTemplate;

public interface SmsTemplateService {

    SmsTemplate getTemplateByName(AppDefinition appDef, String templateName);

    Collection<SmsTemplate> getAllTemplateOptions(AppDefinition appDef);
}
