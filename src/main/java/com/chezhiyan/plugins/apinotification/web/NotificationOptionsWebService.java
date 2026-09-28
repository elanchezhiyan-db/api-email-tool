package com.chezhiyan.plugins.apinotification.web;

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

import com.chezhiyan.plugins.apinotification.model.NotificationTemplate;
import com.chezhiyan.plugins.apinotification.model.SmsTemplate;
import com.chezhiyan.plugins.apinotification.service.NotificationTemplateService;
import com.chezhiyan.plugins.apinotification.service.SmsTemplateService;
import com.chezhiyan.plugins.apinotification.util.CpaNotificationConstants;

public class NotificationOptionsWebService {

    public void service(
            HttpServletRequest request,
            HttpServletResponse response,
            AppDefinition appDef,
            NotificationTemplateService templateService,
            SmsTemplateService smsTemplateService) throws IOException {

        String action = request.getParameter(CpaNotificationConstants.PARAM_ACTION);

        JSONArray result = new JSONArray();

        try {
            if (CpaNotificationConstants.ACTION_LIST_FORMS.equals(action)) {

                result = listForms(appDef);

            } else if (CpaNotificationConstants.ACTION_LIST_TEMPLATES.equals(action)) {

                result = listAllTemplates(appDef, templateService);

            } else if (CpaNotificationConstants.ACTION_LIST_SMS_TEMPLATES.equals(action)) {

                result = listAllSmsTemplates(appDef, smsTemplateService);

            } else if (CpaNotificationConstants.ACTION_LIST_FIELDS.equals(action)) {

                String formDefId = request.getParameter(
                        CpaNotificationConstants.PARAM_ATTACHMENTS_FORM);
                if (formDefId == null || formDefId.trim().isEmpty()) {
                    formDefId = request.getParameter(
                            CpaNotificationConstants.PARAM_TEMPLATE_FORM);
                }

                result = listFields(appDef, formDefId);
            }

        } catch (Exception e) {

            LogUtil.error(
                    getClass().getName(),
                    e,
                    "CPA Notification: failed to load AJAX options"
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

        result.put(blankOption());

        Collection<FormDefinition> forms =
                appDef.getFormDefinitionList();

        if (forms == null) {
            return result;
        }

        for (FormDefinition form : forms) {
            result.put(option(form.getId(), form.getName()));
        }

        return result;
    }

    private JSONArray listAllTemplates(AppDefinition appDef, NotificationTemplateService templateService) {
        JSONArray result = new JSONArray();
        if (appDef == null) {
            return result;
        }
        Collection<NotificationTemplate> options = templateService.getAllTemplateOptions(appDef);
        for (NotificationTemplate opt : options) {
            result.put(option(opt.getId(), opt.getTemplateName()));
        }
        return result;
    }

    /**
     * SMS templates are selected by name rather than by record id, matching how the
     * Idelli SMS Tool looked templates up, so the option value is the template name.
     */
    private JSONArray listAllSmsTemplates(AppDefinition appDef, SmsTemplateService smsTemplateService) {
        JSONArray result = new JSONArray();
        if (appDef == null) {
            return result;
        }
        result.put(blankOption());
        Collection<SmsTemplate> options = smsTemplateService.getAllTemplateOptions(appDef);
        for (SmsTemplate opt : options) {
            result.put(option(opt.getTemplateName(), opt.getTemplateName()));
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

        result.put(blankOption());

        Collection<Map<String, String>> columns =
                FormUtil.getFormColumns(appDef, formDefId);

        for (Map<String, String> col : columns) {
            result.put(option(col.get("value"), col.get("label")));
        }

        return result;
    }

    private JSONObject blankOption() {
        return option("", "");
    }

    private JSONObject option(String value, String label) {
        JSONObject option = new JSONObject();
        option.put("value", value);
        option.put("label", label);
        return option;
    }
}
