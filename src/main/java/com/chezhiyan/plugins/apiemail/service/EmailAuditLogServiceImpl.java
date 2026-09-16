package com.chezhiyan.plugins.apiemail.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import com.chezhiyan.plugins.apiemail.model.EmailAuditLog;

public class EmailAuditLogServiceImpl implements EmailAuditLogService {

    private static final String TABLE_NAME = "email_audit_log";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS email_audit_log (" +
            "  id VARCHAR(64) PRIMARY KEY," +
            "  sentBy VARCHAR(200)," +
            "  sentAt VARCHAR(30)," +
            "  mode VARCHAR(20)," +
            "  templateName VARCHAR(200)," +
            "  recipientsTo MEDIUMTEXT," +
            "  recipientsCc MEDIUMTEXT," +
            "  recipientsBcc MEDIUMTEXT," +
            "  attemptNumber VARCHAR(10)," +
            "  responseCode VARCHAR(10)," +
            "  status VARCHAR(20)," +
            "  errorMessage MEDIUMTEXT" +
            ")";

    private static final String INSERT_SQL =
            "INSERT INTO email_audit_log (" +
            "  id, sentBy, sentAt, mode, templateName, recipientsTo, recipientsCc, recipientsBcc," +
            "  attemptNumber, responseCode, status, errorMessage" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public void save(EmailAuditLog auditLog) {
        Connection conn = null;
        try {
            conn = getConnection();
            createTableIfNotExists(conn);

            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, nvl(auditLog.getSentBy()));
                ps.setString(3, nvl(auditLog.getSentAt()));
                ps.setString(4, nvl(auditLog.getMode()));
                ps.setString(5, nvl(auditLog.getTemplateName()));
                ps.setString(6, nvl(auditLog.getRecipientsTo()));
                ps.setString(7, nvl(auditLog.getRecipientsCc()));
                ps.setString(8, nvl(auditLog.getRecipientsBcc()));
                ps.setString(9, nvl(auditLog.getAttemptNumber()));
                ps.setString(10, nvl(auditLog.getResponseCode()));
                ps.setString(11, nvl(auditLog.getStatus()));
                ps.setString(12, nvl(auditLog.getErrorMessage()));
                ps.executeUpdate();
            }

            LogUtil.info(getClass().getName(),
                    "API Email Tool: audit log saved - sentBy=" + auditLog.getSentBy()
                    + " template=" + auditLog.getTemplateName()
                    + " attempt=" + auditLog.getAttemptNumber()
                    + " status=" + auditLog.getStatus());
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "API Email Tool: failed to save audit log");
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void createTableIfNotExists(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(CREATE_TABLE_SQL);
        }
    }

    private Connection getConnection() throws Exception {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        return ds.getConnection();
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}