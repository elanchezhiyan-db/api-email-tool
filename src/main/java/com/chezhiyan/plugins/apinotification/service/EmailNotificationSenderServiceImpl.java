package com.chezhiyan.plugins.apinotification.service;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collection;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.PluginException;
import org.joget.workflow.model.WorkflowAssignment;
import org.json.JSONArray;
import org.json.JSONObject;

import com.chezhiyan.plugins.apinotification.util.CpaNotificationConstants;

public class EmailNotificationSenderServiceImpl extends AbstractApiNotificationSender
        implements EmailNotificationSenderService {

    private static final String[] SUPPORTED_EXTENSIONS = {
        ".pdf", ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp"
    };

    @Override
    public void send(
            final WorkflowAssignment assignment,
            final AppDefinition appDef,
            final String apiUrl,
            final String apiUrlWithAttachments,
            final String to,
            final String cc,
            final String bcc,
            final String subject,
            final String body,
            final String resolvedAttachmentsJson,
            final String triggeredBy,
            final String templateName,
            final String channel) {

        try {
            Collection<String> toList = AppUtil.getEmailList(null, to, assignment, appDef);
            if (toList == null || toList.isEmpty()) {
                throw new PluginException("CPA Notification: no \"To\" recipient specified for the email channel");
            }

            Collection<String> ccList = null;
            if (!isBlank(cc)) {
                ccList = AppUtil.getEmailList(null, cc, assignment, appDef);
            }

            Collection<String> bccList = null;
            if (!isBlank(bcc)) {
                bccList = AppUtil.getEmailList(null, bcc, assignment, appDef);
            }

            JSONObject payload = new JSONObject();
            payload.put("recipients", collectionToArray(toList));
            payload.put("cc", ccList != null ? collectionToArray(ccList) : new JSONArray());
            payload.put("bcc", bccList != null ? collectionToArray(bccList) : new JSONArray());
            payload.put("subject", nvl(subject));
            payload.put("content", nvl(body));

            JSONArray attachmentList = buildAttachmentList(resolvedAttachmentsJson);
            payload.put("attachments", attachmentList);

            String targetUrl = apiUrl;
            if (attachmentList.length() > 0 && !isBlank(apiUrlWithAttachments)) {
                targetUrl = apiUrlWithAttachments;
            }

            dispatch(
                    channel,
                    targetUrl,
                    payload.toString(),
                    "attachments=" + attachmentList.length()
                    + " to=" + joinAddresses(toList)
                    + " subject=" + nvl(subject),
                    triggeredBy,
                    templateName,
                    joinAddresses(toList),
                    joinAddresses(ccList),
                    joinAddresses(bccList),
                    nvl(subject));

        } catch (PluginException e) {
            throw e;
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "CPA Notification: failed to prepare email API request");
        }
    }

    private JSONArray buildAttachmentList(String resolvedAttachmentsJson) {
        JSONArray result = new JSONArray();
        if (isBlank(resolvedAttachmentsJson)) {
            return result;
        }
        try {
            JSONArray attachments = new JSONArray(resolvedAttachmentsJson);
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject att = attachments.getJSONObject(i);
                String path = att.optString("path", "");
                String name = att.optString("name", "");
                if (isBlank(path)) {
                    LogUtil.warn(getClass().getName(),
                            "CPA Notification: skipped attachment with empty path");
                    continue;
                }
                if (isBlank(name)) {
                    name = new File(path).getName();
                }
                if (!isSupportedFileType(name)) {
                    LogUtil.warn(getClass().getName(),
                            "CPA Notification: skipped unsupported file type: " + name);
                    continue;
                }
                File file = new File(path);
                if (!file.exists() || !file.isFile()) {
                    LogUtil.warn(getClass().getName(),
                            "CPA Notification: attachment file not found: " + path);
                    continue;
                }
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String fileBase64 = Base64.getEncoder().encodeToString(fileBytes);

                JSONObject attachment = new JSONObject();
                attachment.put("name", name);
                attachment.put("bytes", fileBase64);
                result.put(attachment);
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "CPA Notification: failed to build attachment payload");
        }
        return result;
    }

    private boolean isSupportedFileType(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private JSONArray collectionToArray(Collection<String> collection) {
        JSONArray arr = new JSONArray();
        if (collection == null) {
            return arr;
        }
        for (String address : collection) {
            arr.put(StringUtil.encodeEmail(address));
        }
        return arr;
    }
}
