package com.chezhiyan.plugins.apiemail.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import com.chezhiyan.plugins.apiemail.model.ConfigurationTemplate;
import com.chezhiyan.plugins.apiemail.util.ApiEmailToolConstants;

public class ConfigurationTemplateServiceImpl implements ConfigurationTemplateService {

    private static final String FORM_ID = "configuration_templates";
    private static final String TABLE_NAME = "configurations";

    private static final String[] NAME_FIELD_PREFERENCE = {
        "configurationName", "configName", "templateName", "name"
    };

    private static final Set<String> SKIP_FIELDS = new HashSet<String>(
            Arrays.asList(ApiEmailToolConstants.SYSTEM_FIELDS));

    @Override
    public Collection<ConfigurationTemplate> getAllConfigurationTemplateOptions(AppDefinition appDef) {
        Collection<ConfigurationTemplate> options = new ArrayList<ConfigurationTemplate>();
        try {
            String tableName = resolveTableName(appDef, FORM_ID, TABLE_NAME);
            FormRowSet rows = getFormDataDao().find(FORM_ID, tableName, null, null, null, false, null, null);
            if (rows == null) {
                return options;
            }
            for (FormRow row : rows) {
                ConfigurationTemplate t = mapConfigurationTemplate(row);
                if (t != null) {
                    options.add(t);
                }
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Failed to load configuration templates");
        }
        return options;
    }

    @Override
    public ConfigurationTemplate getConfiguration(AppDefinition appDef, String configurationTemplateId) {
        if (configurationTemplateId == null || configurationTemplateId.trim().isEmpty()) {
            return null;
        }
        try {
            String tableName = resolveTableName(appDef, FORM_ID, TABLE_NAME);
            FormRow row = getFormDataDao().load(FORM_ID, tableName, configurationTemplateId);
            if (row == null) {
                return null;
            }
            ConfigurationTemplate config = mapConfigurationTemplate(row);
            if (config == null) {
                return null;
            }
            config.setTo(row.getProperty(ApiEmailToolConstants.CONFIG_FIELD_TO));
            config.setCc(row.getProperty(ApiEmailToolConstants.CONFIG_FIELD_CC));
            config.setBcc(row.getProperty(ApiEmailToolConstants.CONFIG_FIELD_BCC));
            return config;
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Failed to load configuration template");
            return null;
        }
    }

    private ConfigurationTemplate mapConfigurationTemplate(FormRow row) {
        String name = null;

        for (String nameField : NAME_FIELD_PREFERENCE) {
            String value = row.getProperty(nameField);
            if (!EmailTemplateServiceImpl.isBlank(value)) {
                name = value.trim();
                break;
            }
        }

        if (name == null) {
            String shortest = null;
            for (String key : row.stringPropertyNames()) {
                if (SKIP_FIELDS.contains(key)) {
                    continue;
                }
                String value = row.getProperty(key);
                if (EmailTemplateServiceImpl.isBlank(value) || value.contains("<")) {
                    continue;
                }
                if (shortest == null || value.length() < shortest.length()) {
                    shortest = value;
                }
            }
            name = shortest;
        }

        if (name == null) {
            return null;
        }
        return new ConfigurationTemplate(row.getId(), name);
    }

    private FormDataDao getFormDataDao() {
        return (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
    }

    private String resolveTableName(AppDefinition appDef, String formDefId, String defaultTable) {
        if (appDef != null && appDef.getFormDefinitionList() != null) {
            for (FormDefinition formDef : appDef.getFormDefinitionList()) {
                if (formDefId.equals(formDef.getId())) {
                    String table = formDef.getTableName();
                    if (!EmailTemplateServiceImpl.isBlank(table)) {
                        return table;
                    }
                }
            }
        }
        return defaultTable;
    }
}
