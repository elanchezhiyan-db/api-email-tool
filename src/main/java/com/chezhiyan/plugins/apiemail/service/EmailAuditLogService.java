package com.chezhiyan.plugins.apiemail.service;

import com.chezhiyan.plugins.apiemail.model.EmailAuditLog;

public interface EmailAuditLogService {

    void save(EmailAuditLog auditLog);
}
