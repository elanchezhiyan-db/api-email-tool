package com.chezhiyan.plugins.apinotification.util;

public final class CpaNotificationConstants {

    private CpaNotificationConstants() {
    }

    // Plugin properties
    public static final String PROP_CHANNEL = "channel";

    public static final String PROP_TEMPLATE = "template";
    public static final String PROP_DYNAMIC_PARAMS = "dynamicParams";

    // Email channel properties
    public static final String PROP_TO = "to";
    public static final String PROP_CC = "cc";
    public static final String PROP_BCC = "bcc";
    public static final String PROP_API_URL = "apiUrl";
    public static final String PROP_API_URL_WITH_ATTACHMENTS = "apiUrlWithAttachments";
    public static final String PROP_ATTACHMENTS_FORM = "attachmentsForm";
    public static final String PROP_FORM_UPLOAD_FIELDS = "formUploadFields";
    public static final String PROP_FILES = "files";

    // SMS channel properties
    public static final String PROP_SMS_TEMPLATE = "smsTemplate";
    public static final String PROP_SMS_DYNAMIC_PARAMETERS = "smsDynamicParameters";
    public static final String PROP_SMS_RECIPIENTS = "smsRecipients";
    public static final String PROP_SMS_API_URL = "smsApiUrl";

    // SMS template form, ported from the Idelli SMS Tool.
    // Templates are looked up by name, not by record id.
    public static final String SMS_TEMPLATE_FORM_ID = "smsTemplate";
    public static final String SMS_TEMPLATE_TABLE = "sms_form";
    public static final String SMS_TEMPLATE_NAME_FIELD = "template_name";
    public static final String SMS_TEMPLATE_MESSAGE_FIELD = "message";

    // SMS audit history form. Kept on the Joget form so existing history
    // written by the Idelli SMS Tool stays readable.
    public static final String SMS_AUDIT_FORM_ID = "smsAuditHistory";
    public static final String SMS_AUDIT_TABLE = "smsAuditHistory";

    // SMS retry configuration
    public static final int SMS_MAX_ATTEMPTS = 3;
    public static final long SMS_RETRY_DELAY_MS = 2000L;

    // Channel values
    public static final String CHANNEL_EMAIL = "email";
    public static final String CHANNEL_SMS = "sms";
    public static final String CHANNEL_BOTH = "both";

    // Notification Template Form.
    // Kept as "email_templates" for backward compatibility with existing template
    // records; the same template supplies the subject (email) and body (SMS).
    public static final String TEMPLATE_FORM_ID = "email_templates";

    // Template form system fields to skip when auto-detecting subject/body
    public static final String[] SYSTEM_FIELDS = {
        "id", "version", "dateCreated", "dateModified",
        "createdBy", "modifiedBy", "createdByName", "modifiedByName"
    };

    // Grid column keys for form upload fields
    public static final String GRID_FIELD_ID = "fieldId";
    public static final String GRID_RECORD_ID = "recordId";
    public static final String GRID_EMBED = "embed";

    // Grid column keys for dynamic parameters
    public static final String GRID_KEY = "key";
    public static final String GRID_VALUE = "value";

    // Grid column keys for the SMS dynamic parameters grid
    public static final String GRID_SMS_FIELD_ID = "fieldId";
    public static final String GRID_SMS_LABEL = "label";

    // AJAX
    public static final String PARAM_ACTION = "action";
    public static final String PARAM_TEMPLATE_FORM = "templateForm";
    public static final String PARAM_ATTACHMENTS_FORM = "attachmentsForm";

    public static final String ACTION_LIST_FORMS = "forms";
    public static final String ACTION_LIST_TEMPLATES = "templates";
    public static final String ACTION_LIST_SMS_TEMPLATES = "smsTemplates";
    public static final String ACTION_LIST_FIELDS = "listFields";
}
