package com.chezhiyan.plugins.apinotification.service;

import com.chezhiyan.plugins.apinotification.model.NotificationAuditLog;

public interface NotificationAuditLogService {

    void save(NotificationAuditLog auditLog);
}
