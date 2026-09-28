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

import com.chezhiyan.plugins.apiemail.model.EmailTemplate;
import com.chezhiyan.plugins.apiemail.util.ApiEmailToolConstants;

public class EmailTemplateServiceImpl implements EmailTemplateService {

    private static final String FORM_ID = "email_templates";
    private static final String TABLE_NAME = "templates";

    private static final Set<String> SKIP_FIELDS = new HashSet<String>(
            Arrays.asList(ApiEmailToolConstants.SYSTEM_FIELDS));

    @Override
    public EmailTemplate getTemplate(AppDefinition appDef, String formId, String templateId) {
        if (templateId == null || templateId.trim().isEmpty()) {
            return null;
        }
        try {
            String tableName = resolveTableName(appDef, formId, TABLE_NAME);
            FormRow row = getFormDataDao().load(FORM_ID, tableName, templateId);
            if (row == null) {
                return null;
            }
            return mapTemplate(row);
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Failed to load email template");
            return null;
        }
    }

    @Override
    public Collection<EmailTemplate> getTemplates(AppDefinition appDef, String formId) {
        return getAllTemplateOptions(appDef);
    }

    @Override
    public Collection<EmailTemplate> getAllTemplateOptions(AppDefinition appDef) {
        Collection<EmailTemplate> templates = new ArrayList<EmailTemplate>();
        try {
            String tableName = resolveTableName(appDef, FORM_ID, TABLE_NAME);
            FormRowSet rows = getFormDataDao().find(FORM_ID, tableName, null, null, null, false, null, null);
            if (rows == null) {
                return templates;
            }
            for (FormRow row : rows) {
                EmailTemplate t = mapTemplate(row);
                if (t != null) {
                    templates.add(t);
                }
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Failed to load email templates");
        }
        return templates;
    }

    private EmailTemplate mapTemplate(FormRow row) {
        String templateNameValue = null;
        String subjectValue = null;
        String bodyValue = null;
        for (String key : row.stringPropertyNames()) {
            if (SKIP_FIELDS.contains(key)) {
                continue;
            }
            String value = row.getProperty(key);
            if (isBlank(value)) {
                continue;
            }
            if (value.contains("<")) {
                bodyValue = value;
            } else if (subjectValue == null || value.length() < subjectValue.length()) {
                subjectValue = value;
            }
        }
        templateNameValue = subjectValue;
        if (isBlank(templateNameValue) && isBlank(subjectValue) && isBlank(bodyValue)) {
            return null;
        }
        return new EmailTemplate(row.getId(), templateNameValue, subjectValue, bodyValue);
    }

    private FormDataDao getFormDataDao() {
        return (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
    }

    private String resolveTableName(AppDefinition appDef, String formDefId, String defaultTable) {
        if (appDef != null && appDef.getFormDefinitionList() != null) {
            for (FormDefinition formDef : appDef.getFormDefinitionList()) {
                if (formDefId.equals(formDef.getId())) {
                    String table = formDef.getTableName();
                    if (!isBlank(table)) {
                        return table;
                    }
                }
            }
        }
        return defaultTable;
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
