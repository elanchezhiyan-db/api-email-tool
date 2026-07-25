package com.chezhiyan.plugins.apiemail.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

    @Override
    public void send(
            final WorkflowAssignment assignment,
            final AppDefinition appDef,
            final String apiUrl,
            final String apiKey,
            final String from,
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
            payload.put("from", from != null ? from : "");
            payload.put("to", collectionToArray(toList));
            if (ccList != null && !ccList.isEmpty()) {
                payload.put("cc", collectionToArray(ccList));
            }
            if (bccList != null && !bccList.isEmpty()) {
                payload.put("bcc", collectionToArray(bccList));
            }
            payload.put("subject", subject != null ? subject : "");
            payload.put("body", body != null ? body : "");

            if (resolvedAttachmentsJson != null && !resolvedAttachmentsJson.trim().isEmpty()) {
                JSONArray attachments = new JSONArray(resolvedAttachmentsJson);
                JSONArray attachmentList = new JSONArray();
                for (int i = 0; i < attachments.length(); i++) {
                    JSONObject att = attachments.getJSONObject(i);
                    JSONObject attPayload = new JSONObject();
                    attPayload.put("path", att.optString("path", ""));
                    attPayload.put("name", att.optString("name", ""));
                    attPayload.put("embed", att.optBoolean("embed", false));
                    attachmentList.put(attPayload);
                }
                payload.put("attachments", attachmentList);
            }

            final String requestBody = payload.toString();
            final String toLog = joinAddresses(toList);

            Thread apiThread = new PluginThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        LogUtil.info(ApiEmailSenderServiceImpl.class.getName(),
                                "API Email Tool: sending to API, url=" + apiUrl
                                + " to=" + toLog + " subject=" + subject);

                        int responseCode = postToApi(apiUrl, apiKey, requestBody);

                        if (responseCode >= 200 && responseCode < 300) {
                            LogUtil.info(ApiEmailSenderServiceImpl.class.getName(),
                                    "API Email Tool: API responded with " + responseCode);
                        } else {
                            LogUtil.error(ApiEmailSenderServiceImpl.class.getName(),
                                    null, "API Email Tool: API returned " + responseCode);
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

    private int postToApi(String apiUrl, String apiKey, String jsonBody) throws Exception {
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            return conn.getResponseCode();
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
}
