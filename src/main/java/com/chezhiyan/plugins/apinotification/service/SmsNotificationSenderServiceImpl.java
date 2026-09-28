package com.chezhiyan.plugins.apinotification.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.json.JSONObject;

import com.chezhiyan.plugins.apinotification.util.CpaNotificationConstants;

/**
 * SMS transport for the CPA Notification Plugin.
 *
 * Ported from the Idelli SMS Tool: the gateway is called once per mobile number
 * with a {"mobile","message"} payload, each number gets its own retry cycle, and
 * every attempt is recorded in the smsAuditHistory form.
 *
 * The gateway call is deliberately kept byte for byte identical to the standalone
 * Idelli SMS Tool that is known to work: same method, same single Content-Type
 * header, same timeouts, same payload keys and same success rule (HTTP 2xx). Do not
 * add headers here without re-testing against the live gateway - a header change is
 * what turns a working call into a "400 Bad Request" from stricter gateways.
 */
public class SmsNotificationSenderServiceImpl extends AbstractApiNotificationSender
        implements SmsNotificationSenderService {

    private static final String CONTENT_TYPE_JSON_UTF8 = "application/json; charset=UTF-8";

    /**
     * Characters a gateway does not expect inside a mobile number: spaces, dashes,
     * dots, brackets and any other formatting a form field may carry.
     */
    private static final Pattern NON_MOBILE_CHARS = Pattern.compile("[^0-9+]");

    /** Characters that separate several numbers inside a single recipients value. */
    private static final Pattern NUMBER_SEPARATORS = Pattern.compile("[,;\\s]+");

    public SmsNotificationSenderServiceImpl() {
        // Keep the 15s connect / 15s read timeouts the Idelli SMS Tool used.
        connectTimeoutMillis = 15000;
        readTimeoutMillis = 15000;
    }

    @Override
    public void send(
            final String smsApiUrl,
            final String recipients,
            final String message,
            final String templateName) {

        final List<String> mobileNumbers = splitMobileNumbers(recipients);

        if (mobileNumbers.isEmpty()) {
            LogUtil.error(getClass().getName(), null,
                    "CPA Notification: no mobile number(s) provided; aborting SMS");
            return;
        }

        LogUtil.info(getClass().getName(),
                "CPA Notification: sending SMS to " + mobileNumbers.size()
                + " number(s) via " + nvl(smsApiUrl)
                + " template=" + nvl(templateName));

        // Fire and forget so a slow gateway does not block the workflow activity.
        Thread smsThread = new PluginThread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (String mobileNumber : mobileNumbers) {
                        sendSmsWithRetry(mobileNumber, smsApiUrl, templateName, message);
                    }
                } catch (Exception e) {
                    LogUtil.error(getClass().getName(), e,
                            "CPA Notification: SMS send failed");
                }
            }
        });
        smsThread.setDaemon(true);
        smsThread.start();
    }

    /**
     * Sends to a single mobile number with up to SMS_MAX_ATTEMPTS attempts.
     * Attempt 1, on failure retry, on failure retry. Each attempt records its own
     * audit history row.
     */
    private void sendSmsWithRetry(
            String mobileNumber,
            String apiUrl,
            String templateName,
            String message) {

        for (int attempt = 1; attempt <= CpaNotificationConstants.SMS_MAX_ATTEMPTS; attempt++) {

            LogUtil.info(getClass().getName(),
                    "CPA Notification: SMS attempt " + attempt + " of "
                    + CpaNotificationConstants.SMS_MAX_ATTEMPTS
                    + " - sending to " + maskMobileNumber(mobileNumber));

            if (attemptSendSms(mobileNumber, apiUrl, templateName, message, attempt)) {
                LogUtil.info(getClass().getName(),
                        "CPA Notification: SMS attempt " + attempt + " succeeded for "
                        + maskMobileNumber(mobileNumber));
                return;
            }

            LogUtil.error(getClass().getName(), null,
                    "CPA Notification: SMS attempt " + attempt + " failed for "
                    + maskMobileNumber(mobileNumber));

            if (attempt < CpaNotificationConstants.SMS_MAX_ATTEMPTS) {
                try {
                    Thread.sleep(CpaNotificationConstants.SMS_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LogUtil.error(getClass().getName(), ie,
                            "CPA Notification: SMS retry delay interrupted before attempt "
                            + (attempt + 1));
                    return;
                }
            }
        }

        LogUtil.error(getClass().getName(), null,
                "CPA Notification: SMS failed after " + CpaNotificationConstants.SMS_MAX_ATTEMPTS
                + " attempts for " + maskMobileNumber(mobileNumber));
    }

    /**
     * Keeps the last four characters so a number can be correlated with the audit row
     * without writing the number itself into the log.
     */
    private String maskMobileNumber(String mobileNumber) {
        if (mobileNumber == null) {
            return "";
        }
        int visible = Math.min(4, mobileNumber.length());
        return "****" + mobileNumber.substring(mobileNumber.length() - visible);
    }

    /**
     * Performs a single HTTP send attempt. Returns true only on a 2xx response.
     * Any exception counts as a failed attempt rather than a fatal error, so control
     * always returns to the retry loop. The audit row is written on every path so
     * each attempt is recorded.
     */
    private boolean attemptSendSms(
            String mobileNumber,
            String apiUrl,
            String templateName,
            String message,
            int attemptNumber) {

        int responseCode = -1;
        String responseBody = "";
        boolean success = false;

        if (isBlank(apiUrl)) {
            LogUtil.error(getClass().getName(), null,
                    "CPA Notification: SMS API URL is not configured; aborting attempt "
                    + attemptNumber);
            saveAuditRecord(mobileNumber, templateName, message, attemptNumber,
                    false, -1, "SMS API URL not configured");
            return false;
        }

        HttpURLConnection conn = null;

        try {
            URL url = new URL(apiUrl.trim());

            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(connectTimeoutMillis);
            conn.setReadTimeout(readTimeoutMillis);
            conn.setRequestProperty("Content-Type", CONTENT_TYPE_JSON_UTF8);

            JSONObject payload = new JSONObject();
            payload.put("mobile", mobileNumber);
            payload.put("message", nvl(message));
            String jsonBody = payload.toString();

            // The payload and the number are never logged: only the target, the size of the
            // request and the gateway answer, so a "400 Bad Request" can be diagnosed without
            // writing phone numbers or message text into the log.
            LogUtil.info(getClass().getName(),
                    "CPA Notification: SMS attempt " + attemptNumber
                    + " - POST " + apiUrl + " bytes=" + jsonBody.length());

            OutputStream os = conn.getOutputStream();
            try {
                byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(bytes, 0, bytes.length);
                os.flush();
            } finally {
                os.close();
            }

            responseCode = conn.getResponseCode();
            success = responseCode >= 200 && responseCode < 300;

            responseBody = readResponseBody(conn, success);

            LogUtil.info(getClass().getName(),
                    "CPA Notification: SMS attempt " + attemptNumber
                    + " - API response code: " + responseCode + " | body: " + responseBody);

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "CPA Notification: SMS attempt " + attemptNumber + " - send error");
            responseBody = "Exception: " + e.getMessage();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        saveAuditRecord(mobileNumber, templateName, message, attemptNumber,
                success, responseCode, responseBody);
        return success;
    }

    /**
     * Reads the gateway response body. A gateway is free to answer without a body, so a
     * missing stream is not an error - it must not hide the status code that the audit
     * record needs.
     */
    private String readResponseBody(HttpURLConnection conn, boolean success) {
        InputStream stream = null;
        BufferedReader reader = null;

        try {
            stream = success ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) {
                return "";
            }
            reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line.trim());
            }
            return response.toString();
        } catch (Exception e) {
            LogUtil.warn(getClass().getName(),
                    "CPA Notification: could not read SMS API response body: " + e.getMessage());
            return "";
        } finally {
            closeQuietly(reader);
        }
    }

    private void closeQuietly(BufferedReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (Exception e) {
            LogUtil.warn(getClass().getName(),
                    "CPA Notification: failed to close SMS API response stream: " + e.getMessage());
        }
    }

    /**
     * Persists one audit history record for one attempt to one mobile number.
     */
    private void saveAuditRecord(
            String mobileNumber,
            String templateName,
            String message,
            int attemptNumber,
            boolean success,
            int responseCode,
            String responseBody) {

        try {
            FormDataDao formDataDao =
                    (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

            FormRow auditRow = new FormRow();
            auditRow.setId(UUID.randomUUID().toString());
            auditRow.setProperty("mobile_number", mobileNumber);
            auditRow.setProperty("template_name", nvl(templateName));
            auditRow.setProperty("message_content", nvl(message));
            auditRow.setProperty("attempt_number", String.valueOf(attemptNumber));
            auditRow.setProperty("status", success ? "SUCCESS" : "FAILED");
            auditRow.setProperty("response_code", String.valueOf(responseCode));
            auditRow.setProperty("response_body", nvl(responseBody));
            auditRow.setProperty("sent_date",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

            FormRowSet auditRowSet = new FormRowSet();
            auditRowSet.add(auditRow);

            formDataDao.saveOrUpdate(
                    CpaNotificationConstants.SMS_AUDIT_FORM_ID,
                    CpaNotificationConstants.SMS_AUDIT_TABLE,
                    auditRowSet);

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "CPA Notification: failed to save SMS audit history record (attempt "
                    + attemptNumber + ")");
        }
    }

    /**
     * Splits a recipients value into individual, gateway ready numbers.
     *
     * The value may be a comma, semicolon or whitespace separated list, and a single
     * entry may still carry formatting such as "+968 9123 4567" or "(968) 9123-4567".
     * Everything is trimmed and stripped so the gateway receives plain digits with an
     * optional leading "+", which is the only form the Idelli SMS Tool ever sent.
     * Anything that still does not start with a digit is dropped and logged, so a bad
     * entry cannot turn into a "400 Bad Request" for the whole batch.
     */
    private List<String> splitMobileNumbers(String recipientsProperty) {

        List<String> numbers = new ArrayList<>();

        if (isBlank(recipientsProperty)) {
            return numbers;
        }

        Set<String> seen = new LinkedHashSet<>();

        for (String part : NUMBER_SEPARATORS.split(recipientsProperty.trim())) {
            if (part.isEmpty()) {
                continue;
            }
            seen.add(part);
        }

        for (String raw : seen) {
            String cleaned = normaliseMobileNumber(raw);
            if (cleaned == null) {
                // The raw value is not logged: a rejected entry is either an unresolved hash
                // variable or a number the gateway would reject, and both are identified by
                // the length and the hash variable shape.
                LogUtil.error(getClass().getName(), null,
                        "CPA Notification: skipped a recipients entry that is not a usable mobile "
                        + "number (length=" + raw.length()
                        + ", looksLikeHashVariable=" + (raw.startsWith("#") && raw.endsWith("#"))
                        + ")");
                continue;
            }
            if (numbers.contains(cleaned)) {
                continue;
            }
            numbers.add(cleaned);
        }

        return numbers;
    }

    /**
     * Strips every non mobile character and keeps a single leading "+".
     * Returns null when the result is not a usable number.
     */
    private String normaliseMobileNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        boolean hasPlus = trimmed.startsWith("+");
        String digitsOnly = NON_MOBILE_CHARS.matcher(trimmed).replaceAll("");
        if (hasPlus) {
            digitsOnly = digitsOnly.replace("+", "");
        }
        if (digitsOnly.isEmpty() || !Character.isDigit(digitsOnly.charAt(0))) {
            return null;
        }
        return hasPlus ? "+" + digitsOnly : digitsOnly;
    }
}
