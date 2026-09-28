package com.chezhiyan.plugins.apinotification;

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

import com.chezhiyan.plugins.apinotification.model.NotificationTemplate;
import com.chezhiyan.plugins.apinotification.model.SmsTemplate;
import com.chezhiyan.plugins.apinotification.service.EmailNotificationSenderService;
import com.chezhiyan.plugins.apinotification.service.EmailNotificationSenderServiceImpl;
import com.chezhiyan.plugins.apinotification.service.NotificationTemplateService;
import com.chezhiyan.plugins.apinotification.service.NotificationTemplateServiceImpl;
import com.chezhiyan.plugins.apinotification.service.SmsNotificationSenderService;
import com.chezhiyan.plugins.apinotification.service.SmsNotificationSenderServiceImpl;
import com.chezhiyan.plugins.apinotification.service.SmsTemplateService;
import com.chezhiyan.plugins.apinotification.service.SmsTemplateServiceImpl;
import com.chezhiyan.plugins.apinotification.util.CpaNotificationConstants;
import com.chezhiyan.plugins.apinotification.util.SmsMessageBuilder;
import com.chezhiyan.plugins.apinotification.web.NotificationOptionsWebService;

public class CpaNotificationPlugin extends DefaultApplicationPlugin implements PluginWebSupport {

    private final NotificationTemplateService templateService = new NotificationTemplateServiceImpl();
    private final SmsTemplateService smsTemplateService = new SmsTemplateServiceImpl();
    private final EmailNotificationSenderService emailSenderService = new EmailNotificationSenderServiceImpl();
    private final SmsNotificationSenderService smsSenderService = new SmsNotificationSenderServiceImpl();
    private final NotificationOptionsWebService optionsWebService = new NotificationOptionsWebService();

    @Override
    public String getName() {
        return "CPA Notification Plugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Sends notifications over an email and/or SMS channel via API, using a DB-backed template form with hash variable support.";
    }

    @Override
    public String getLabel() {
        return "CPA Notification Plugin";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/cpaNotificationPlugin.json", null, true, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map properties) {
        WorkflowAssignment assignment = (WorkflowAssignment) properties.get("workflowAssignment");
        AppDefinition appDef = (AppDefinition) properties.get("appDef");

        try {
            String channel = (String) properties.get(CpaNotificationConstants.PROP_CHANNEL);
            if (isBlank(channel)) {
                channel = CpaNotificationConstants.CHANNEL_EMAIL;
            }

            String triggeredBy = resolveTriggeredBy(assignment);

            if (sendsEmail(channel)) {
                sendEmail(properties, appDef, assignment, triggeredBy);
            }

            if (sendsSms(channel)) {
                sendSms(properties, appDef, assignment);
            }

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "CPA Notification failed to execute");
        }

        return null;
    }

    private boolean sendsEmail(String channel) {
        return CpaNotificationConstants.CHANNEL_EMAIL.equals(channel)
                || CpaNotificationConstants.CHANNEL_BOTH.equals(channel);
    }

    private boolean sendsSms(String channel) {
        return CpaNotificationConstants.CHANNEL_SMS.equals(channel)
                || CpaNotificationConstants.CHANNEL_BOTH.equals(channel);
    }

    private void sendEmail(
            Map properties,
            AppDefinition appDef,
            WorkflowAssignment assignment,
            String triggeredBy) {

        String templateId = (String) properties.get(CpaNotificationConstants.PROP_TEMPLATE);
        if (isBlank(templateId)) {
            throw new PluginException("CPA Notification: no email template selected");
        }

        NotificationTemplate template = templateService.getTemplate(
                appDef, CpaNotificationConstants.TEMPLATE_FORM_ID, templateId);
        if (template == null) {
            throw new PluginException("CPA Notification: email template not found in form");
        }
        if (isBlank(template.getSubject())) {
            LogUtil.warn(getClass().getName(), "CPA Notification: template has empty subject");
        }
        if (isBlank(template.getBody())) {
            LogUtil.warn(getClass().getName(), "CPA Notification: template has empty body");
        }

        String subject = AppUtil.processHashVariable(
                template.getSubject(), assignment, null, null, appDef);
        String body = AppUtil.processHashVariable(
                template.getBody(), assignment, null, null, appDef);

        Object rawDynamicParams = properties.get(CpaNotificationConstants.PROP_DYNAMIC_PARAMS);
        if (rawDynamicParams instanceof Object[]) {
            for (Object o : (Object[]) rawDynamicParams) {
                Map mapping = (Map) o;
                Object keyObj = mapping.get(CpaNotificationConstants.GRID_KEY);
                Object valueObj = mapping.get(CpaNotificationConstants.GRID_VALUE);
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
                        "CPA Notification: dynamic parameter replaced " + key + " = " + value);
            }
        }

        String to = (String) properties.get(CpaNotificationConstants.PROP_TO);
        String cc = (String) properties.get(CpaNotificationConstants.PROP_CC);
        String bcc = (String) properties.get(CpaNotificationConstants.PROP_BCC);
        String apiUrl = (String) properties.get(CpaNotificationConstants.PROP_API_URL);
        String apiUrlWithAttachments =
                (String) properties.get(CpaNotificationConstants.PROP_API_URL_WITH_ATTACHMENTS);

        to = AppUtil.processHashVariable(to, assignment, null, null, appDef);
        cc = AppUtil.processHashVariable(cc, assignment, null, null, appDef);
        bcc = AppUtil.processHashVariable(bcc, assignment, null, null, appDef);
        apiUrl = AppUtil.processHashVariable(apiUrl, assignment, null, null, appDef);
        apiUrlWithAttachments =
                AppUtil.processHashVariable(apiUrlWithAttachments, assignment, null, null, appDef);

        String resolvedAttachmentsJson = resolveAttachments(properties, appDef, assignment);

        emailSenderService.send(
                assignment, appDef,
                apiUrl, apiUrlWithAttachments,
                to, cc, bcc, subject, body,
                resolvedAttachmentsJson,
                triggeredBy,
                template.getTemplateName(),
                CpaNotificationConstants.CHANNEL_EMAIL
        );
    }

    private void sendSms(
            Map properties,
            AppDefinition appDef,
            WorkflowAssignment assignment) {

        String templateName = resolveSmsProperty(
                properties, CpaNotificationConstants.PROP_SMS_TEMPLATE, appDef, assignment);
        if (isBlank(templateName)) {
            LogUtil.error(getClass().getName(), null,
                    "CPA Notification: no SMS template selected");
            return;
        }

        SmsTemplate template = smsTemplateService.getTemplateByName(appDef, templateName);
        if (template == null) {
            LogUtil.error(getClass().getName(), null,
                    "CPA Notification: SMS template not found in form: " + templateName);
            return;
        }

        Object rawDynamicParameters =
                properties.get(CpaNotificationConstants.PROP_SMS_DYNAMIC_PARAMETERS);
        String message = SmsMessageBuilder.build(
                template.getMessage(), rawDynamicParameters, assignment, appDef);

        String smsApiUrl = resolveSmsProperty(
                properties, CpaNotificationConstants.PROP_SMS_API_URL, appDef, assignment);
        String smsRecipients = resolveSmsProperty(
                properties, CpaNotificationConstants.PROP_SMS_RECIPIENTS, appDef, assignment);

        LogUtil.info(getClass().getName(),
                "CPA Notification: SMS resolved - template=" + templateName
                + " recipientEntries=" + countEntries(smsRecipients)
                + " messageLength=" + (message != null ? message.length() : 0));

        smsSenderService.send(smsApiUrl, smsRecipients, message, templateName);
    }

    /**
     * Counts the entries in a recipients value without logging the numbers themselves.
     */
    private int countEntries(String recipients) {
        if (recipients == null || recipients.trim().isEmpty()) {
            return 0;
        }
        return recipients.trim().split("[,;\\s]+").length;
    }

    /**
     * Reads an SMS property and resolves any hash variable it still holds.
     *
     * The raw properties map only resolves hash variables when Joget hands the plugin
     * its wrapped, hash aware map. That wrapper is not present on every execution path
     * (userview triggered tools, form triggers, PluginManager.execute), so a value such
     * as "#form.customer.mobile#" used to reach the gateway as that literal text and
     * every number was answered with "400 Bad Request". getPropertyString() performs the
     * same resolution the standalone Idelli SMS Tool relies on, and the explicit
     * processHashVariable() call is the safety net for the paths without the wrapper.
     */
    private String resolveSmsProperty(
            Map properties,
            String key,
            AppDefinition appDef,
            WorkflowAssignment assignment) {

        String value = getPropertyString(key);

        if (value == null) {
            Object raw = properties != null ? properties.get(key) : null;
            value = raw != null ? raw.toString() : "";
        }

        String resolved = value;

        try {
            resolved = AppUtil.processHashVariable(value, assignment, null, null, appDef);
        } catch (Exception e) {
            LogUtil.warn(getClass().getName(),
                    "CPA Notification: could not resolve hash variables in SMS property "
                    + key + ": " + e.getMessage());
        }

        if (resolved == null) {
            resolved = "";
        }

        if (!value.equals(resolved)) {
            // The recipients value is not echoed: a property that still holds a hash variable
            // is the case worth seeing, and the resolved number itself must stay out of the log.
            boolean sensitive = CpaNotificationConstants.PROP_SMS_RECIPIENTS.equals(key);
            LogUtil.info(getClass().getName(),
                    "CPA Notification: resolved SMS property " + key
                    + (sensitive
                    ? " (unresolvedLength=" + value.length()
                    + ", resolvedCount=" + countEntries(resolved) + ")"
                    : " - " + value + " -> " + resolved));
        }

        return resolved.trim();
    }

    private String resolveTriggeredBy(WorkflowAssignment assignment) {
        String user = null;
        if (assignment != null) {
            try {
                user = assignment.getProcessRequesterId();
            } catch (Exception e) {
                LogUtil.warn(getClass().getName(),
                        "CPA Notification: could not resolve process requester: " + e.getMessage());
            }
            if (user == null || user.trim().isEmpty()) {
                try {
                    user = assignment.getAssigneeId();
                } catch (Exception e) {
                    LogUtil.warn(getClass().getName(),
                            "CPA Notification: could not resolve assignee: " + e.getMessage());
                }
            }
        }
        return user;
    }

    private String resolveAttachments(
            Map properties,
            AppDefinition appDef,
            WorkflowAssignment assignment) {

        JSONArray result = new JSONArray();

        try {
            String attachmentsFormId =
                    (String) properties.get(CpaNotificationConstants.PROP_ATTACHMENTS_FORM);

            Object rawFields = properties.get(CpaNotificationConstants.PROP_FORM_UPLOAD_FIELDS);
            Object[] fieldsArr = rawFields instanceof Object[] ? (Object[]) rawFields : null;

            if (fieldsArr != null && fieldsArr.length > 0 && !isBlank(attachmentsFormId)) {
                org.joget.apps.app.service.AppService appService =
                        (org.joget.apps.app.service.AppService) AppUtil.getApplicationContext().getBean("appService");

                for (Object o : fieldsArr) {
                    Map mapping = (Map) o;

                    Object fieldIdObj = mapping.get(CpaNotificationConstants.GRID_FIELD_ID);
                    if (fieldIdObj == null) {
                        continue;
                    }
                    String gridFieldId = fieldIdObj.toString();
                    if (isBlank(gridFieldId)) {
                        continue;
                    }

                    boolean embed = "true".equals(
                            mapping.get(CpaNotificationConstants.GRID_EMBED));

                    Object recordIdObj = mapping.get(CpaNotificationConstants.GRID_RECORD_ID);
                    String recordId = recordIdObj != null ? recordIdObj.toString() : null;
                    recordId = AppUtil.processHashVariable(recordId, assignment, null, null, appDef);
                    if (isBlank(recordId) && assignment != null) {
                        recordId = appService.getOriginProcessId(assignment.getProcessId());
                    }

                    LogUtil.info(getClass().getName(),
                            "CPA Notification: loading form upload field, form=" + attachmentsFormId
                            + " field=" + gridFieldId + " recordId=" + recordId);

                    org.joget.apps.form.model.FormData formDataObj = new org.joget.apps.form.model.FormData();
                    formDataObj.setPrimaryKeyValue(recordId);
                    Form loadForm = appService.viewDataForm(
                            appDef.getId(), appDef.getVersion().toString(),
                            attachmentsFormId, null, null, null, formDataObj, null, null);

                    if (loadForm == null) {
                        LogUtil.warn(getClass().getName(),
                                "CPA Notification: form not found: " + attachmentsFormId);
                        continue;
                    }

                    org.joget.apps.form.model.Element el =
                            org.joget.apps.form.service.FormUtil.findElement(gridFieldId, loadForm, formDataObj);
                    if (el == null) {
                        LogUtil.warn(getClass().getName(),
                                "CPA Notification: field not found in form: " + gridFieldId);
                        continue;
                    }

                    String value = org.joget.apps.form.service.FormUtil.getElementPropertyValue(el, formDataObj);
                    if (isBlank(value)) {
                        LogUtil.warn(getClass().getName(),
                                "CPA Notification: field value is empty: " + gridFieldId);
                        continue;
                    }

                    LogUtil.info(getClass().getName(),
                            "CPA Notification: field value = " + value);

                    String[] fileNames = value.split(";");
                    for (String fileName : fileNames) {
                        fileName = fileName.trim();
                        if (fileName.isEmpty()) {
                            continue;
                        }

                        File file = FileUtil.getFile(fileName, el, recordId);
                        if (file == null || !file.exists()) {
                            LogUtil.warn(getClass().getName(),
                                    "CPA Notification: form upload file not found: " + fileName);
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

            Object rawFiles = properties.get(CpaNotificationConstants.PROP_FILES);
            LogUtil.info(getClass().getName(),
                    "CPA Notification: files raw type=" + (rawFiles != null ? rawFiles.getClass().getName() : "null")
                    + " value=" + rawFiles);
            Object[] filesArr = null;
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
                    LogUtil.error(getClass().getName(), ex, "CPA Notification: failed to parse files string");
                }
            }

            if (filesArr != null && filesArr.length > 0) {
                LogUtil.info(getClass().getName(),
                        "CPA Notification: processing " + filesArr.length + " file grid entries");
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
                            "CPA Notification: processing file attachment path=" + path + " type=" + type);

                    path = AppUtil.processHashVariable(path, assignment, null, null, appDef);
                    fileName = AppUtil.processHashVariable(fileName, assignment, null, null, appDef);

                    if ("URL".equals(type)) {
                        File resolvedFile = resolveJogetFormUrl(path, appDef);
                        if (resolvedFile != null && resolvedFile.exists()) {
                            path = resolvedFile.getAbsolutePath();
                            if (isBlank(fileName)) {
                                fileName = resolvedFile.getName();
                            }
                            type = "System Path";
                            LogUtil.info(getClass().getName(),
                                    "CPA Notification: resolved Joget form URL to local file: " + path);
                        }
                    }

                    JSONObject att = new JSONObject();
                    att.put("path", path);
                    att.put("name", fileName);
                    att.put("type", type);
                    att.put("embed", embed);
                    result.put(att);
                }
            }

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "CPA Notification: failed to resolve attachments");
        }

        LogUtil.info(getClass().getName(),
                "CPA Notification: resolved " + result.length() + " attachment(s)");

        return result.toString();
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        if (appDef == null) {
            appDef = resolveAppFromRequest(request);
        }
        optionsWebService.service(request, response, appDef, templateService, smsTemplateService);
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
            LogUtil.warn(getClass().getName(), "CPA Notification: could not resolve app from request: " + e.getMessage());
        }
        return null;
    }

    private File findFileInElement(
            org.joget.apps.form.model.Element element,
            String fileName,
            String recordId) {

        try {
            File file = FileUtil.getFile(fileName, element, recordId);
            if (file != null && file.exists()) {
                return file;
            }
        } catch (Exception ignored) {
        }

        java.util.Collection<org.joget.apps.form.model.Element> children = element.getChildren();
        if (children != null) {
            for (org.joget.apps.form.model.Element child : children) {
                File found = findFileInElement(child, fileName, recordId);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private File resolveJogetFormUrl(String urlString, AppDefinition appDef) {
        try {
            java.net.URL url = new java.net.URL(urlString);
            String path = url.getPath();

            String pattern = "/form/download/";
            int idx = path.indexOf(pattern);
            if (idx < 0) {
                return null;
            }

            String after = path.substring(idx + pattern.length());
            String[] parts = after.split("/");
            if (parts.length < 3) {
                return null;
            }

            String formId = parts[0];
            String recordId = parts[1];
            String fileName = parts[2];

            LogUtil.info(getClass().getName(),
                    "CPA Notification: parsing Joget form URL, formId=" + formId
                    + " recordId=" + recordId + " fileName=" + fileName);

            org.joget.apps.app.service.AppService appService =
                    (org.joget.apps.app.service.AppService) AppUtil.getApplicationContext().getBean("appService");

            org.joget.apps.form.model.FormData formDataObj = new org.joget.apps.form.model.FormData();
            formDataObj.setPrimaryKeyValue(recordId);
            Form loadForm = appService.viewDataForm(
                    appDef.getId(), appDef.getVersion().toString(),
                    formId, null, null, null, formDataObj, null, null);

            if (loadForm == null) {
                LogUtil.warn(getClass().getName(),
                        "CPA Notification: form not found for Joget URL: " + formId);
                return null;
            }

            File found = findFileInElement(loadForm, fileName, recordId);
            if (found != null) {
                return found;
            }

            LogUtil.warn(getClass().getName(),
                    "CPA Notification: could not find file " + fileName + " in form " + formId);
            return null;

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "CPA Notification: failed to resolve Joget form URL: " + urlString);
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }
}
