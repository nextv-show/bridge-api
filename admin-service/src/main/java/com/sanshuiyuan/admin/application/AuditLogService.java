package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.domain.AuditLog;
import com.sanshuiyuan.admin.infra.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditLogService {

    private final AuditLogRepository repo;

    public AuditLogService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void log(Long adminId, String action, String targetType,
                    String targetId, String payloadJson) {
        String ip = resolveIp();
        repo.save(AuditLog.of(adminId, action, targetType, targetId, payloadJson, ip));
    }

    private String resolveIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "system";
            var req = attrs.getRequest();
            String xfwd = req.getHeader("X-Forwarded-For");
            if (xfwd != null && !xfwd.isBlank()) return xfwd.split(",")[0].trim();
            return req.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
