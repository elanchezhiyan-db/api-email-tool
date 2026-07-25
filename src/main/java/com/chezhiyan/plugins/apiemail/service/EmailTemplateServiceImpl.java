package com.chezhiyan.plugins.apiemail.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.joget.apps.app.dao.FormDefinitionDao;
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

    private static final Set<String> SKIP_FIELDS = new HashSet<String>(
            Arrays.asList(ApiEmailToolConstants.SYSTEM_FIELDS));

    @Override
    public EmailTemplate getTemplate(
            AppDefinition appDef,
            String formId,
            String templateId) {

        if (appDef == null
                || isBlank(formId)
                || isBlank(templateId)) {

            return null;
        }

        try {

            String tableName =
                    resolveTableName(appDef, formId);

            if (tableName == null) {
                return null;
            }

            FormRow row =
                    getFormDataDao().load(
                            formId,
                            tableName,
                            templateId
                    );

            if (row == null) {
                return null;
            }

            return mapTemplate(row);

        } catch (Exception e) {

            LogUtil.error(
                    getClass().getName(),
                    e,
                    "Failed to load email template"
            );

            return null;
        }
    }

    @Override
    public Collection<EmailTemplate> getTemplates(
            AppDefinition appDef,
            String formId) {

        Collection<EmailTemplate> templates =
                new ArrayList<EmailTemplate>();

        if (appDef == null || isBlank(formId)) {
            return templates;
        }

        try {

            String tableName =
                    resolveTableName(appDef, formId);

            if (tableName == null) {
                return templates;
            }

            FormRowSet rows =
                    getFormDataDao().find(
                            formId,
                            tableName,
                            null,
                            null,
                            null,
                            false,
                            null,
                            null
                    );

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

            LogUtil.error(
                    getClass().getName(),
                    e,
                    "Failed to load email templates"
            );
        }

        return templates;
    }

    private EmailTemplate mapTemplate(FormRow row) {

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
            } else if (subjectValue == null
                    || value.length() < subjectValue.length()) {
                subjectValue = value;
            }
        }

        if (isBlank(subjectValue) && isBlank(bodyValue)) {
            return null;
        }

        EmailTemplate template =
                new EmailTemplate();

        template.setId(row.getId());
        template.setTemplateName(subjectValue);
        template.setSubject(subjectValue);
        template.setBody(bodyValue);

        return template;
    }

    private String resolveTableName(
            AppDefinition appDef,
            String formId) {

        FormDefinitionDao dao =
                getFormDefinitionDao();

        FormDefinition form =
                dao.loadById(
                        formId,
                        appDef
                );

        if (form == null) {

            LogUtil.warn(
                    getClass().getName(),
                    "Template form not found: " + formId
            );

            return null;
        }

        return form.getTableName();
    }

    private FormDataDao getFormDataDao() {

        return (FormDataDao) AppUtil
                .getApplicationContext()
                .getBean("formDataDao");
    }

    private FormDefinitionDao getFormDefinitionDao() {

        return (FormDefinitionDao) AppUtil
                .getApplicationContext()
                .getBean("formDefinitionDao");
    }

    private boolean isBlank(String value) {

        return value == null
                || value.trim().isEmpty();
    }
}
