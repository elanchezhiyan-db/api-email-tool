package com.chezhiyan.plugins.apiemail;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.service.FileUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginException;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.json.JSONArray;
import org.json.JSONObject;

import com.chezhiyan.plugins.apiemail.model.EmailTemplate;
import com.chezhiyan.plugins.apiemail.service.ApiEmailSenderService;
import com.chezhiyan.plugins.apiemail.service.ApiEmailSenderServiceImpl;
import com.chezhiyan.plugins.apiemail.service.EmailTemplateService;
import com.chezhiyan.plugins.apiemail.service.EmailTemplateServiceImpl;
import com.chezhiyan.plugins.apiemail.util.ApiEmailToolConstants;
import com.chezhiyan.plugins.apiemail.web.EmailTemplateOptionsWebService;

public class ApiEmailTool extends DefaultApplicationPlugin implements PluginWebSupport {

    private final EmailTemplateService templateService = new EmailTemplateServiceImpl();
    private final ApiEmailSenderService apiEmailSenderService = new ApiEmailSenderServiceImpl();
    private final EmailTemplateOptionsWebService optionsWebService = new EmailTemplateOptionsWebService();

    @Override
    public String getName() {
        return "API Email Tool (Template)";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Sends an email via API using a DB-backed template form with hash variable support.";
    }

    @Override
    public String getLabel() {
        return "API Email Tool (Template)";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/emailApiTool.json", null, true, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map properties) {
        WorkflowAssignment assignment = (WorkflowAssignment) properties.get("workflowAssignment");
        AppDefinition appDef = (AppDefinition) properties.get("appDef");

        try {
            String from = (String) properties.get(ApiEmailToolConstants.PROP_FROM);
            String to = (String) properties.get(ApiEmailToolConstants.PROP_TO);
            String cc = (String) properties.get(ApiEmailToolConstants.PROP_CC);
            String bcc = (String) properties.get(ApiEmailToolConstants.PROP_BCC);

            String apiUrl = (String) properties.get(ApiEmailToolConstants.PROP_API_URL);
            String apiKey = (String) properties.get(ApiEmailToolConstants.PROP_API_KEY);

            String templateFormId = (String) properties.get(ApiEmailToolConstants.PROP_TEMPLATE_FORM);
            String templateId = (String) properties.get(ApiEmailToolConstants.PROP_TEMPLATE);

            if (isBlank(templateFormId) || isBlank(templateId)) {
                throw new PluginException("API Email Tool: no template selected");
            }

            EmailTemplate template = templateService.getTemplate(appDef, templateFormId, templateId);
            if (template == null) {
                throw new PluginException("API Email Tool: template not found in form");
            }
            if (isBlank(template.getSubject())) {
                LogUtil.warn(getClass().getName(), "API Email Tool: template has empty subject");
            }
            if (isBlank(template.getBody())) {
                LogUtil.warn(getClass().getName(), "API Email Tool: template has empty body");
            }

            String subject = AppUtil.processHashVariable(template.getSubject(), assignment, null, null, appDef);
            String body = AppUtil.processHashVariable(template.getBody(), assignment, null, null, appDef);

            Object[] dynamicParamsArr = null;
            if (properties.get(ApiEmailToolConstants.PROP_DYNAMIC_PARAMS) instanceof Object[]) {
                dynamicParamsArr = (Object[]) properties.get(ApiEmailToolConstants.PROP_DYNAMIC_PARAMS);
            }
            if (dynamicParamsArr != null && dynamicParamsArr.length > 0) {
                for (Object o : dynamicParamsArr) {
                    Map mapping = (Map) o;
                    Object keyObj = mapping.get(ApiEmailToolConstants.GRID_KEY);
                    Object valueObj = mapping.get(ApiEmailToolConstants.GRID_VALUE);
                    if (keyObj == null || valueObj == null) {
                        continue;
                    }
                    String key = keyObj.toString().trim();
                    String value = valueObj.toString().trim();
                    if (key.isEmpty() || value.isEmpty()) {
                        continue;
                    }
                    value = AppUtil.processHashVariable(value, assignment, null, null, appDef);
                    body = body.replaceAll(key, value);
                    LogUtil.info(getClass().getName(),
                            "API Email Tool: dynamic parameter replaced " + key + " = " + value);
                }
            }

            from = AppUtil.processHashVariable(from, assignment, null, null, appDef);
            to = AppUtil.processHashVariable(to, assignment, null, null, appDef);
            cc = AppUtil.processHashVariable(cc, assignment, null, null, appDef);
            bcc = AppUtil.processHashVariable(bcc, assignment, null, null, appDef);
            apiUrl = AppUtil.processHashVariable(apiUrl, assignment, null, null, appDef);
            apiKey = AppUtil.processHashVariable(apiKey, assignment, null, null, appDef);

            String resolvedAttachmentsJson = resolveAttachments(
                    properties, appDef, assignment,
                    templateFormId, templateId);

            apiEmailSenderService.send(
                    assignment, appDef,
                    apiUrl, apiKey,
                    from, to, cc, bcc, subject, body,
                    resolvedAttachmentsJson
            );

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "API Email Tool failed to execute");
        }

        return null;
    }

    private String resolveAttachments(
            Map properties,
            AppDefinition appDef,
            WorkflowAssignment assignment,
            String templateFormId,
            String templateId) {

        JSONArray result = new JSONArray();

        try {
            String attachmentsFormId =
                    (String) properties.get(ApiEmailToolConstants.PROP_ATTACHMENTS_FORM);

            Object[] fieldsArr = null;
            if (properties.get(ApiEmailToolConstants.PROP_FORM_UPLOAD_FIELDS) instanceof Object[]) {
                fieldsArr = (Object[]) properties.get(ApiEmailToolConstants.PROP_FORM_UPLOAD_FIELDS);
            }

            if (fieldsArr != null && fieldsArr.length > 0 && !isBlank(attachmentsFormId)) {
                org.joget.apps.app.service.AppService appService =
                        (org.joget.apps.app.service.AppService) AppUtil.getApplicationContext().getBean("appService");

                for (Object o : fieldsArr) {
                    Map mapping = (Map) o;

                    Object fieldIdObj = mapping.get(ApiEmailToolConstants.GRID_FIELD_ID);
                    if (fieldIdObj == null) {
                        continue;
                    }
                    String gridFieldId = fieldIdObj.toString();
                    if (isBlank(gridFieldId)) {
                        continue;
                    }

                    boolean embed = "true".equals(
                            mapping.get(ApiEmailToolConstants.GRID_EMBED));

                    Object recordIdObj = mapping.get(ApiEmailToolConstants.GRID_RECORD_ID);
                    String recordId = recordIdObj != null ? recordIdObj.toString() : null;
                    recordId = AppUtil.processHashVariable(recordId, assignment, null, null, appDef);
                    if (isBlank(recordId) && assignment != null) {
                        recordId = appService.getOriginProcessId(assignment.getProcessId());
                    }

                    LogUtil.info(getClass().getName(),
                            "API Email Tool: loading form upload field, form=" + attachmentsFormId
                            + " field=" + gridFieldId + " recordId=" + recordId);

                    org.joget.apps.form.model.FormData formDataObj = new org.joget.apps.form.model.FormData();
                    formDataObj.setPrimaryKeyValue(recordId);
                    Form loadForm = appService.viewDataForm(
                            appDef.getId(), appDef.getVersion().toString(),
                            attachmentsFormId, null, null, null, formDataObj, null, null);

                    if (loadForm == null) {
                        LogUtil.warn(getClass().getName(),
                                "API Email Tool: form not found: " + attachmentsFormId);
                        continue;
                    }

                    org.joget.apps.form.model.Element el =
                            org.joget.apps.form.service.FormUtil.findElement(gridFieldId, loadForm, formDataObj);
                    if (el == null) {
                        LogUtil.warn(getClass().getName(),
                                "API Email Tool: field not found in form: " + gridFieldId);
                        continue;
                    }

                    String value = org.joget.apps.form.service.FormUtil.getElementPropertyValue(el, formDataObj);
                    if (isBlank(value)) {
                        LogUtil.warn(getClass().getName(),
                                "API Email Tool: field value is empty: " + gridFieldId);
                        continue;
                    }

                    LogUtil.info(getClass().getName(),
                            "API Email Tool: field value = " + value);

                    String[] fileNames = value.split(";");
                    for (String fileName : fileNames) {
                        fileName = fileName.trim();
                        if (fileName.isEmpty()) {
                            continue;
                        }

                        File file = FileUtil.getFile(fileName, el, recordId);
                        if (file == null || !file.exists()) {
                            LogUtil.warn(getClass().getName(),
                                    "API Email Tool: form upload file not found: " + fileName);
                            continue;
                        }

                        JSONObject att = new JSONObject();
                        att.put("path", file.getAbsolutePath());
                        att.put("name", fileName);
                        att.put("embed", embed);
                        result.put(att);
                    }
                }
            }

            Object[] filesArr = null;
            Object rawFiles = properties.get(ApiEmailToolConstants.PROP_FILES);
            LogUtil.info(getClass().getName(),
                    "API Email Tool: files raw type=" + (rawFiles != null ? rawFiles.getClass().getName() : "null")
                    + " value=" + rawFiles);
            if (rawFiles instanceof Object[]) {
                filesArr = (Object[]) rawFiles;
            } else if (rawFiles instanceof String && !isBlank((String)rawFiles)) {
                try {
                    JSONArray ja = new JSONArray((String)rawFiles);
                    filesArr = new Object[ja.length()];
                    for (int i = 0; i < ja.length(); i++) {
                        filesArr[i] = ja.getJSONObject(i).toMap();
                    }
                } catch (Exception ex) {
                    LogUtil.error(getClass().getName(), ex, "API Email Tool: failed to parse files string");
                }
            }

            if (filesArr != null && filesArr.length > 0) {
                LogUtil.info(getClass().getName(),
                        "API Email Tool: processing " + filesArr.length + " file grid entries");
                for (Object o : filesArr) {
                    Map mapping = (Map) o;

                    Object pathObj = mapping.get("path");
                    if (pathObj == null) {
                        continue;
                    }
                    String path = pathObj.toString();
                    if (isBlank(path)) {
                        continue;
                    }

                    String type = mapping.get("type") != null
                            ? mapping.get("type").toString() : "System Path";
                    String fileName = mapping.get("fileName") != null
                            ? mapping.get("fileName").toString() : "";
                    boolean embed = "true".equals(mapping.get("embed"));

                    LogUtil.info(getClass().getName(),
                            "API Email Tool: processing file attachment path=" + path + " type=" + type);

                    path = AppUtil.processHashVariable(path, assignment, null, null, appDef);
                    fileName = AppUtil.processHashVariable(fileName, assignment, null, null, appDef);

                    JSONObject att = new JSONObject();
                    att.put("path", path);
                    att.put("name", fileName);
                    att.put("embed", embed);
                    result.put(att);
                }
            }

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "API Email Tool: failed to resolve attachments");
        }

        LogUtil.info(getClass().getName(),
                "API Email Tool: resolved " + result.length() + " attachment(s)");

        return result.toString();
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        if (appDef == null) {
            appDef = resolveAppFromRequest(request);
        }
        optionsWebService.service(request, response, appDef, templateService);
    }

    private AppDefinition resolveAppFromRequest(HttpServletRequest request) {
        try {
            String referer = request.getHeader("Referer");
            if (referer != null && referer.contains("/app/")) {
                int idx = referer.indexOf("/app/");
                String after = referer.substring(idx + 5);
                String[] parts = after.split("[/?#]");
                if (parts.length >= 2) {
                    String appId = parts[0];
                    String version = parts[1];
                    org.joget.apps.app.service.AppService appService =
                            (org.joget.apps.app.service.AppService) AppUtil.getApplicationContext().getBean("appService");
                    return appService.getAppDefinition(appId, version);
                }
            }
        } catch (Exception e) {
            LogUtil.warn(getClass().getName(), "Could not resolve app from request: " + e.getMessage());
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }
}
