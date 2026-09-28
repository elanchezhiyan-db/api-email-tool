package com.chezhiyan.plugins.apinotification.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import com.chezhiyan.plugins.apinotification.model.SmsTemplate;
import com.chezhiyan.plugins.apinotification.util.CpaNotificationConstants;

public class SmsTemplateServiceImpl implements SmsTemplateService {

    @Override
    public SmsTemplate getTemplateByName(AppDefinition appDef, String templateName) {
        if (isBlank(templateName)) {
            return null;
        }
        try {
            String tableName = resolveTableName(appDef);
            FormRowSet rows = getFormDataDao().find(
                    CpaNotificationConstants.SMS_TEMPLATE_FORM_ID,
                    tableName,
                    "where c_" + CpaNotificationConstants.SMS_TEMPLATE_NAME_FIELD + " = ?",
                    new String[]{templateName},
                    null, null, null, null);

            if (rows == null || rows.isEmpty()) {
                return null;
            }

            FormRow row = rows.iterator().next();
            return new SmsTemplate(
                    row.getId(),
                    row.getProperty(CpaNotificationConstants.SMS_TEMPLATE_NAME_FIELD),
                    row.getProperty(CpaNotificationConstants.SMS_TEMPLATE_MESSAGE_FIELD));
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Failed to load SMS template: " + templateName);
            return null;
        }
    }

    @Override
    public Collection<SmsTemplate> getAllTemplateOptions(AppDefinition appDef) {
        Collection<SmsTemplate> templates = new ArrayList<SmsTemplate>();
        try {
            String tableName = resolveTableName(appDef);
            FormRowSet rows = getFormDataDao().find(
                    CpaNotificationConstants.SMS_TEMPLATE_FORM_ID,
                    tableName, null, null, null, null, null, null);

            if (rows == null) {
                return templates;
            }

            Set<String> seen = new LinkedHashSet<String>();
            for (FormRow row : rows) {
                String name = row.getProperty(CpaNotificationConstants.SMS_TEMPLATE_NAME_FIELD);
                if (isBlank(name) || !seen.add(name)) {
                    continue;
                }
                templates.add(new SmsTemplate(
                        row.getId(),
                        name,
                        row.getProperty(CpaNotificationConstants.SMS_TEMPLATE_MESSAGE_FIELD)));
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Failed to load SMS templates");
        }
        return templates;
    }

    private String resolveTableName(AppDefinition appDef) {
        String defaultTable = CpaNotificationConstants.SMS_TEMPLATE_TABLE;
        if (appDef != null && appDef.getFormDefinitionList() != null) {
            for (FormDefinition formDef : appDef.getFormDefinitionList()) {
                if (CpaNotificationConstants.SMS_TEMPLATE_FORM_ID.equals(formDef.getId())) {
                    String table = formDef.getTableName();
                    if (!isBlank(table)) {
                        return table;
                    }
                }
            }
        }
        return defaultTable;
    }

    private FormDataDao getFormDataDao() {
        return (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
