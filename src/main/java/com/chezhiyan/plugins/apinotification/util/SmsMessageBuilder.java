package com.chezhiyan.plugins.apinotification.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;

/**
 * Builds the final SMS message text from a template row.
 *
 * Ported from the Idelli SMS Tool: the template message is read from a rich text
 * editor so HTML tags are stripped, then the Dynamic Parameters grid is applied.
 * Each grid row is evaluated independently, so static text and hash variables can
 * be mixed freely in the same grid.
 */
public final class SmsMessageBuilder {

    /** A hash variable looks like #form.xxx# / #appVariable.xxx# etc. */
    private static final Pattern HASH_VARIABLE_PATTERN = Pattern.compile("^#.+#$");

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    private SmsMessageBuilder() {
    }

    public static String build(
            String rawMessage,
            Object rawDynamicParameters,
            WorkflowAssignment assignment) {
        return build(rawMessage, rawDynamicParameters, assignment, null);
    }

    public static String build(
            String rawMessage,
            Object rawDynamicParameters,
            WorkflowAssignment assignment,
            AppDefinition appDef) {

        if (rawMessage == null) {
            return "";
        }

        String message = HTML_TAG_PATTERN.matcher(rawMessage).replaceAll("");
        Map<String, String> resolved = resolveDynamicParams(rawDynamicParameters, assignment, appDef);

        for (Map.Entry<String, String> entry : resolved.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        return resolveLeftoverHashVariables(message, appDef, assignment);
    }

    private static Map<String, String> resolveDynamicParams(
            Object rawDynamicParameters,
            WorkflowAssignment assignment,
            AppDefinition appDef) {

        Map<String, String> resolved = new LinkedHashMap<>();
        if (!(rawDynamicParameters instanceof Object[])) {
            return resolved;
        }

        for (Object rowObj : (Object[]) rawDynamicParameters) {
            if (!(rowObj instanceof Map)) {
                continue;
            }

            Map<?, ?> row = (Map<?, ?>) rowObj;
            Object fieldIdObj = row.get(CpaNotificationConstants.GRID_SMS_FIELD_ID);
            Object labelObj = row.get(CpaNotificationConstants.GRID_SMS_LABEL);

            if (fieldIdObj == null) {
                continue;
            }

            String fieldId = fieldIdObj.toString();
            if (fieldId.isEmpty()) {
                continue;
            }

            String rawValue = labelObj != null ? labelObj.toString() : "";
            resolved.put(fieldId, resolveValue(rawValue, fieldId, assignment, appDef));
        }

        return resolved;
    }

    /**
     * A value that looks like a hash variable is resolved live against the current
     * process. Anything else is used exactly as typed. A failed resolution falls
     * back to the raw text so the send still proceeds.
     */
    private static String resolveValue(
            String rawValue,
            String fieldId,
            WorkflowAssignment assignment,
            AppDefinition appDef) {

        if (rawValue == null || !HASH_VARIABLE_PATTERN.matcher(rawValue.trim()).matches()) {
            return rawValue;
        }

        String resolved = processHashVariable(rawValue, assignment, appDef);
        if (resolved != null) {
            LogUtil.info(SmsMessageBuilder.class.getName(),
                    "CPA Notification: resolved SMS hash variable for '" + fieldId
                    + "': " + rawValue + " -> " + resolved);
            return resolved;
        }

        return rawValue;
    }

    /**
     * Last pass over the finished message. When a template stores its placeholders as
     * real hash variables, anything the Dynamic Parameters grid did not cover would
     * otherwise be sent to the gateway as literal "#form.xxx#" text, which the gateway
     * rejects. Placeholders that still cannot be resolved are removed so the send stays
     * valid.
     */
    private static String resolveLeftoverHashVariables(
            String message,
            AppDefinition appDef,
            WorkflowAssignment assignment) {

        if (message == null || message.indexOf('#') < 0) {
            return message;
        }

        String resolved = processHashVariable(message, assignment, appDef);
        if (resolved == null || resolved.equals(message)) {
            return message;
        }

        LogUtil.info(SmsMessageBuilder.class.getName(),
                "CPA Notification: resolved remaining SMS hash variables in the message");

        return resolved;
    }

    private static String processHashVariable(
            String value,
            WorkflowAssignment assignment,
            AppDefinition appDef) {

        try {
            return AppUtil.processHashVariable(value, assignment, null, null, appDef);
        } catch (Exception e) {
            LogUtil.error(SmsMessageBuilder.class.getName(), e,
                    "CPA Notification: error resolving SMS hash variable '" + value
                    + "' - falling back to raw text");
            return null;
        }
    }
}
