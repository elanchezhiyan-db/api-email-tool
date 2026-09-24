package com.chezhiyan.plugins.apiemail.service;

import java.util.Collection;

import org.joget.apps.app.model.AppDefinition;

import com.chezhiyan.plugins.apiemail.model.ConfigurationTemplate;

public interface ConfigurationTemplateService {

    Collection<ConfigurationTemplate> getAllConfigurationTemplateOptions(AppDefinition appDef);

    ConfigurationTemplate getConfiguration(AppDefinition appDef, String configurationTemplateId);
}
