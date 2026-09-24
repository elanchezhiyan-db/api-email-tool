package com.chezhiyan.plugins.apiemail.util;

public final class ApiEmailToolConstants {

    private ApiEmailToolConstants() {
    }

    // Plugin properties
    public static final String PROP_MODE = "mode";
    public static final String PROP_TO = "to";
    public static final String PROP_CC = "cc";
    public static final String PROP_BCC = "bcc";

    public static final String PROP_API_URL = "apiUrl";
    public static final String PROP_API_URL_WITH_ATTACHMENTS = "apiUrlWithAttachments";

    public static final String PROP_TEMPLATE_FORM = "templateForm";
    public static final String PROP_TEMPLATE = "template";
    public static final String PROP_ATTACHMENTS_FORM = "attachmentsForm";
    public static final String PROP_FORM_UPLOAD_FIELDS = "formUploadFields";
    public static final String PROP_FILES = "files";
    public static final String PROP_DYNAMIC_PARAMS = "dynamicParams";
    public static final String PROP_CONFIGURATION_TEMPLATE = "configurationTemplate";

    // Configuration template form fields
    public static final String CONFIG_FIELD_TO = "to";
    public static final String CONFIG_FIELD_CC = "cc";
    public static final String CONFIG_FIELD_BCC = "bcc";

    // Mode values
    public static final String MODE_MANUAL = "manual";
    public static final String MODE_AUTO = "auto";

    // Email Template Form — system fields to skip when auto-detecting subject/body
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

    // AJAX
    public static final String PARAM_ACTION = "action";
    public static final String PARAM_TEMPLATE_FORM = "templateForm";
    public static final String PARAM_ATTACHMENTS_FORM = "attachmentsForm";

    public static final String ACTION_LIST_FORMS = "forms";
    public static final String ACTION_LIST_TEMPLATES = "templates";
    public static final String ACTION_LIST_FIELDS = "listFields";
    public static final String ACTION_LIST_CONFIG_TEMPLATES = "configTemplates";
}
