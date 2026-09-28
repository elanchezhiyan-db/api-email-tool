package com.chezhiyan.plugins.apinotification.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;

import com.chezhiyan.plugins.apinotification.model.NotificationAuditLog;

public abstract class AbstractApiNotificationSender {

    protected static final String STATUS_SUCCESS = "SUCCESS";
    protected static final String STATUS_FAILURE = "FAILURE";

    private static final int MAX_ATTEMPTS = 3;

    /** Overridable per channel; the SMS sender keeps the Idelli 15s/15s values. */
    protected int connectTimeoutMillis = 10000;
    protected int readTimeoutMillis = 30000;

    private final NotificationAuditLogService auditLogService = new NotificationAuditLogServiceImpl();

    /**
     * Delivers the payload asynchronously with retries and records the outcome in
     * the audit log. A blank URL is treated as "not configured" and is skipped
     * without an audit entry, matching the pre-existing skip-on-blank behaviour.
     */
    protected void dispatch(
            final String channel,
            final String apiUrl,
            final String requestBody,
            final String summary,
            final String triggeredBy,
            final String templateName,
            final String to,
            final String cc,
            final String bcc,
            final String message) {

        if (isBlank(apiUrl)) {
            LogUtil.info(getClass().getName(),
                    "CPA Notification: " + channel + " API URL is blank, skipping send - " + summary);
            return;
        }

        final String sentBy = nvl(triggeredBy);
        final String template = nvl(templateName);

        Thread apiThread = new PluginThread(new Runnable() {
            @Override
            public void run() {
                int attempt = 0;
                int responseCode = -1;
                String errorMessage = null;
                boolean success = false;

                while (attempt < MAX_ATTEMPTS) {
                    attempt++;
                    try {
                        LogUtil.info(getClass().getName(),
                                "CPA Notification: sending " + channel + " (attempt " + attempt + "/" + MAX_ATTEMPTS
                                + "), url=" + apiUrl + " - " + summary);

                        responseCode = postToApiWithBody(apiUrl, requestBody).getResponseCode();

                        if (responseCode >= 200 && responseCode < 300) {
                            LogUtil.info(getClass().getName(),
                                    "CPA Notification: " + channel + " API responded with " + responseCode
                                    + " on attempt " + attempt);
                            success = true;
                            break;
                        }

                        errorMessage = "API returned " + responseCode;
                        LogUtil.warn(getClass().getName(),
                                "CPA Notification: " + channel + " API returned " + responseCode
                                + " on attempt " + attempt);
                    } catch (Exception e) {
                        errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                        LogUtil.warn(getClass().getName(),
                                "CPA Notification: " + channel + " attempt " + attempt + " failed: " + errorMessage);
                    }

                    if (attempt < MAX_ATTEMPTS) {
                        try {
                            Thread.sleep(1000 * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

                saveAuditLog(channel, sentBy, template, to, cc, bcc, message,
                        attempt, responseCode,
                        success ? STATUS_SUCCESS : STATUS_FAILURE,
                        success ? null : errorMessage);

                if (!success) {
                    LogUtil.error(getClass().getName(), null,
                            "CPA Notification: " + channel + " failed after " + MAX_ATTEMPTS + " attempts"
                            + " responseCode=" + responseCode + " error=" + errorMessage);
                }
            }
        });
        apiThread.setDaemon(true);
        apiThread.start();
    }

    private void saveAuditLog(
            String channel,
            String sentBy,
            String templateName,
            String to,
            String cc,
            String bcc,
            String message,
            int attemptNumber,
            int responseCode,
            String status,
            String errorMessage) {

        try {
            NotificationAuditLog auditLog = new NotificationAuditLog();
            auditLog.setSentBy(sentBy);
            auditLog.setSentAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            auditLog.setChannel(nvl(channel));
            auditLog.setTemplateName(nvl(templateName));
            auditLog.setRecipientsTo(nvl(to));
            auditLog.setRecipientsCc(nvl(cc));
            auditLog.setRecipientsBcc(nvl(bcc));
            auditLog.setMessage(nvl(message));
            auditLog.setAttemptNumber(String.valueOf(attemptNumber));
            auditLog.setResponseCode(String.valueOf(responseCode));
            auditLog.setStatus(nvl(status));
            auditLog.setErrorMessage(nvl(errorMessage));

            auditLogService.save(auditLog);
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "CPA Notification: failed to prepare audit log");
        }
    }

    /**
     * POSTs the JSON body and returns both the status code and the response body.
     * The body is captured so callers such as the SMS sender can log the gateway
     * response for troubleshooting.
     */
    protected ApiResponse postToApiWithBody(String apiUrl, String jsonBody) throws Exception {
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(connectTimeoutMillis);
            conn.setReadTimeout(readTimeoutMillis);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            String body = "";

            if (responseCode >= 400) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), "UTF-8"))) {
                    body = readAll(br);
                }
                LogUtil.error(getClass().getName(), null,
                        "CPA Notification: API error body: " + body);
            } else {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    body = readAll(br);
                }
            }

            return new ApiResponse(responseCode, body);
        } finally {
            conn.disconnect();
        }
    }

    private String readAll(BufferedReader br) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    /** HTTP status code plus response body from a notification API call. */
    protected static final class ApiResponse {

        private final int responseCode;
        private final String body;

        ApiResponse(int responseCode, String body) {
            this.responseCode = responseCode;
            this.body = body;
        }

        int getResponseCode() {
            return responseCode;
        }

        String getBody() {
            return body;
        }
    }

    protected String joinAddresses(Collection<String> collection) {
        if (collection == null || collection.isEmpty()) {
            return "";
        }
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

    protected boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }

    protected String nvl(String s) {
        return s != null ? s : "";
    }
}
