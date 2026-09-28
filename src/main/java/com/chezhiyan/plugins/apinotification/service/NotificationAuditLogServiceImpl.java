package com.chezhiyan.plugins.apinotification.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import com.chezhiyan.plugins.apinotification.model.NotificationAuditLog;

public class NotificationAuditLogServiceImpl implements NotificationAuditLogService {

    private static final String TABLE_NAME = "notification_audit_log";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
            "  id VARCHAR(64) PRIMARY KEY," +
            "  sentBy VARCHAR(200)," +
            "  sentAt VARCHAR(30)," +
            "  channel VARCHAR(20)," +
            "  templateName VARCHAR(200)," +
            "  recipientsTo MEDIUMTEXT," +
            "  recipientsCc MEDIUMTEXT," +
            "  recipientsBcc MEDIUMTEXT," +
            "  message MEDIUMTEXT," +
            "  attemptNumber VARCHAR(10)," +
            "  responseCode VARCHAR(10)," +
            "  status VARCHAR(20)," +
            "  errorMessage MEDIUMTEXT" +
            ")";

    private static final String INSERT_SQL =
            "INSERT INTO " + TABLE_NAME + " (" +
            "  id, sentBy, sentAt, channel, templateName, recipientsTo, recipientsCc, recipientsBcc," +
            "  message, attemptNumber, responseCode, status, errorMessage" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public void save(NotificationAuditLog auditLog) {
        Connection conn = null;
        try {
            conn = getConnection();
            createTableIfNotExists(conn);

            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, nvl(auditLog.getSentBy()));
                ps.setString(3, nvl(auditLog.getSentAt()));
                ps.setString(4, nvl(auditLog.getChannel()));
                ps.setString(5, nvl(auditLog.getTemplateName()));
                ps.setString(6, nvl(auditLog.getRecipientsTo()));
                ps.setString(7, nvl(auditLog.getRecipientsCc()));
                ps.setString(8, nvl(auditLog.getRecipientsBcc()));
                ps.setString(9, nvl(auditLog.getMessage()));
                ps.setString(10, nvl(auditLog.getAttemptNumber()));
                ps.setString(11, nvl(auditLog.getResponseCode()));
                ps.setString(12, nvl(auditLog.getStatus()));
                ps.setString(13, nvl(auditLog.getErrorMessage()));
                ps.executeUpdate();
            }

            LogUtil.info(getClass().getName(),
                    "CPA Notification: audit log saved - channel=" + auditLog.getChannel()
                    + " sentBy=" + auditLog.getSentBy()
                    + " template=" + auditLog.getTemplateName()
                    + " attempt=" + auditLog.getAttemptNumber()
                    + " status=" + auditLog.getStatus());
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "CPA Notification: failed to save audit log");
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
