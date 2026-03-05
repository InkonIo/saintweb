package com.schedule.app.repository;

import com.schedule.app.entity.AuditLog;
import com.schedule.app.enums.AuditAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    List<AuditLog> findAllByEntityTypeAndEntityId(String entityType, Long entityId);
    List<AuditLog> findAllByAction(AuditAction action);
}