package com.chezhiyan.plugins.apiemail.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.PluginException;
import org.joget.workflow.model.WorkflowAssignment;
import org.json.JSONArray;
import org.json.JSONObject;

public class ApiEmailSenderServiceImpl implements ApiEmailSenderService {

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
            final String resolvedAttachmentsJson) {

        try {
            Collection<String> toList = AppUtil.getEmailList(null, to, assignment, appDef);
            if (toList == null || toList.isEmpty()) {
                throw new PluginException("API Email Tool: no \"To\" recipient specified");
            }

            Collection<String> ccList = null;
            if (cc != null && cc.trim().length() != 0) {
                ccList = AppUtil.getEmailList(null, cc, assignment, appDef);
            }

            Collection<String> bccList = null;
            if (bcc != null && bcc.trim().length() != 0) {
                bccList = AppUtil.getEmailList(null, bcc, assignment, appDef);
            }

            JSONObject payload = new JSONObject();
            payload.put("recipients", collectionToArray(toList));
            if (ccList != null && !ccList.isEmpty()) {
                payload.put("cc", collectionToArray(ccList));
            } else {
                payload.put("cc", new JSONArray());
            }
            if (bccList != null && !bccList.isEmpty()) {
                payload.put("bcc", collectionToArray(bccList));
            } else {
                payload.put("bcc", new JSONArray());
            }
            payload.put("subject", subject != null ? subject : "");
            payload.put("content", body != null ? body : "");

            JSONArray attachmentList = buildAttachmentList(resolvedAttachmentsJson);
            payload.put("attachments", attachmentList);

            String targetUrl = apiUrl;
            if (attachmentList.length() > 0) {
                targetUrl = (apiUrlWithAttachments != null && !apiUrlWithAttachments.trim().isEmpty())
                        ? apiUrlWithAttachments : apiUrl;
            }

            final String requestBody = payload.toString();
            final String finalUrl = targetUrl;
            final String toLog = joinAddresses(toList);

            if (finalUrl == null || finalUrl.trim().isEmpty()) {
                LogUtil.info(ApiEmailSenderServiceImpl.class.getName(),
                        "API Email Tool: API URL is blank, skipping send"
                        + " attachments=" + attachmentList.length()
                        + " to=" + toLog + " subject=" + subject);
                return;
            }

            Thread apiThread = new PluginThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int maxAttempts = 3;
                        int attempt = 0;
                        int responseCode = -1;
                        Exception lastException = null;

                        while (attempt < maxAttempts) {
                            attempt++;
                            try {
                                LogUtil.info(ApiEmailSenderServiceImpl.class.getName(),
                                        "API Email Tool: sending to API (attempt " + attempt + "/" + maxAttempts + "), url=" + finalUrl
                                        + " attachments=" + attachmentList.length()
                                        + " to=" + toLog + " subject=" + subject);

                                responseCode = postToApi(finalUrl, requestBody);

                                if (responseCode >= 200 && responseCode < 300) {
                                    LogUtil.info(ApiEmailSenderServiceImpl.class.getName(),
                                            "API Email Tool: API responded with " + responseCode + " on attempt " + attempt);
                                    break;
                                } else {
                                    LogUtil.warn(ApiEmailSenderServiceImpl.class.getName(),
                                            "API Email Tool: API returned " + responseCode + " on attempt " + attempt);
                                    lastException = new Exception("API returned " + responseCode);
                                }
                            } catch (Exception e) {
                                lastException = e;
                                LogUtil.warn(ApiEmailSenderServiceImpl.class.getName(),
                                        "API Email Tool: attempt " + attempt + " failed: " + e.getMessage());
                            }

                            if (attempt < maxAttempts) {
                                try {
                                    Thread.sleep(1000 * attempt);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }

                        if (responseCode < 200 || responseCode >= 300) {
                            if (lastException != null) {
                                LogUtil.error(ApiEmailSenderServiceImpl.class.getName(),
                                        lastException, "API Email Tool: failed after " + maxAttempts + " attempts");
                            } else {
                                LogUtil.error(ApiEmailSenderServiceImpl.class.getName(),
                                        null, "API Email Tool: API returned " + responseCode + " after " + maxAttempts + " attempts");
                            }
                        }
                    } catch (Exception e) {
                        LogUtil.error(ApiEmailSenderServiceImpl.class.getName(),
                                e, "API Email Tool: failed to call API");
                    }
                }
            });
            apiThread.setDaemon(true);
            apiThread.start();

        } catch (PluginException e) {
            throw e;
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "API Email Tool: failed to prepare API request");
        }
    }

    private JSONArray buildAttachmentList(String resolvedAttachmentsJson) {
        JSONArray result = new JSONArray();
        if (resolvedAttachmentsJson == null || resolvedAttachmentsJson.trim().isEmpty()) {
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
                            "API Email Tool: skipped attachment with empty path");
                    continue;
                }
                if (isBlank(name)) {
                    name = new File(path).getName();
                }
                if (!isSupportedFileType(name)) {
                    LogUtil.warn(getClass().getName(),
                            "API Email Tool: skipped unsupported file type: " + name);
                    continue;
                }
                File file = new File(path);
                if (!file.exists() || !file.isFile()) {
                    LogUtil.warn(getClass().getName(),
                            "API Email Tool: attachment file not found: " + path);
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
                    "API Email Tool: failed to build attachment payload");
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

    private int postToApi(String apiUrl, String jsonBody) throws Exception {
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    LogUtil.error(getClass().getName(), null,
                            "API Email Tool: API error body: " + sb.toString());
                }
            }
            return responseCode;
        } finally {
            conn.disconnect();
        }
    }

    private JSONArray collectionToArray(Collection<String> collection) {
        JSONArray arr = new JSONArray();
        for (String address : collection) {
            arr.put(StringUtil.encodeEmail(address));
        }
        return arr;
    }

    private String joinAddresses(Collection<String> collection) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = collection.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }
}