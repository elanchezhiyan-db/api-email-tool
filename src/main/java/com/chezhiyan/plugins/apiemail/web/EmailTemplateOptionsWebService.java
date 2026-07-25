package com.chezhiyan.plugins.apiemail.web;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import com.chezhiyan.plugins.apiemail.model.EmailTemplate;
import com.chezhiyan.plugins.apiemail.service.EmailTemplateService;
import com.chezhiyan.plugins.apiemail.util.ApiEmailToolConstants;

public class EmailTemplateOptionsWebService {

    public void service(
            HttpServletRequest request,
            HttpServletResponse response,
            AppDefinition appDef,
            EmailTemplateService templateService) throws IOException {

        String action = request.getParameter(
                ApiEmailToolConstants.PARAM_ACTION);

        JSONArray result = new JSONArray();

        try {
            if (ApiEmailToolConstants.ACTION_LIST_FORMS.equals(action)) {

                result = listForms(appDef);

            } else if (ApiEmailToolConstants.ACTION_LIST_TEMPLATES.equals(action)) {

                String formId = request.getParameter(
                        ApiEmailToolConstants.PARAM_TEMPLATE_FORM);

                result = listTemplates(
                        appDef,
                        formId,
                        templateService
                );

            } else if (ApiEmailToolConstants.ACTION_LIST_FIELDS.equals(action)) {

                String formDefId = request.getParameter(
                        ApiEmailToolConstants.PARAM_ATTACHMENTS_FORM);

                result = listFields(appDef, formDefId);
            }

        } catch (Exception e) {

            LogUtil.error(
                    getClass().getName(),
                    e,
                    "API Email Tool: failed to load AJAX options"
            );
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(result.toString());
    }

    private JSONArray listForms(AppDefinition appDef) {

        JSONArray result = new JSONArray();

        if (appDef == null) {
            return result;
        }

        JSONObject blank = new JSONObject();
        blank.put("value", "");
        blank.put("label", "");
        result.put(blank);

        Collection<FormDefinition> forms =
                appDef.getFormDefinitionList();

        if (forms == null) {
            return result;
        }

        for (FormDefinition form : forms) {

            JSONObject option = new JSONObject();

            option.put("value", form.getId());
            option.put("label", form.getName());

            result.put(option);
        }

        return result;
    }

    private JSONArray listTemplates(
            AppDefinition appDef,
            String formId,
            EmailTemplateService templateService) {

        JSONArray result = new JSONArray();

        if (appDef == null
                || formId == null
                || formId.trim().isEmpty()) {

            return result;
        }

        Collection<EmailTemplate> templates =
                templateService.getTemplates(
                        appDef,
                        formId
                );

        for (EmailTemplate template : templates) {

            JSONObject option = new JSONObject();

            option.put(
                    "value",
                    template.getId()
            );

            option.put(
                    "label",
                    template.getTemplateName()
            );

            result.put(option);
        }

        return result;
    }

    private JSONArray listFields(
            AppDefinition appDef,
            String formDefId) {

        JSONArray result = new JSONArray();

        if (appDef == null
                || formDefId == null
                || formDefId.trim().isEmpty()) {

            return result;
        }

        JSONObject blank = new JSONObject();
        blank.put("value", "");
        blank.put("label", "");
        result.put(blank);

        Collection<Map<String, String>> columns =
                FormUtil.getFormColumns(appDef, formDefId);

        for (Map<String, String> col : columns) {

            JSONObject option = new JSONObject();

            option.put("value", col.get("value"));
            option.put("label", col.get("label"));

            result.put(option);
        }

        return result;
    }
}
