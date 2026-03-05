package com.schedule.app.service;

import com.schedule.app.dto.response.AuditResponse;
import com.schedule.app.entity.AuditLog;
import com.schedule.app.entity.User;
import com.schedule.app.enums.AuditAction;
import com.schedule.app.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, AuditAction action, String entityType, Long entityId, String details, String ipAddress) {
        AuditLog log = AuditLog.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(log);
    }

    public List<AuditResponse> getAll() {
        return auditLogRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<AuditResponse> getByUser(Long userId) {
    return auditLogRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::toDto)
            .toList();
}

    public List<AuditResponse> getByEntity(String entityType, Long entityId) {
        return auditLogRepository.findAllByEntityTypeAndEntityId(entityType, entityId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private AuditResponse toDto(AuditLog log) {
        return new AuditResponse(
                log.getId(),
                log.getUser() != null ? log.getUser().getUsername() : null,
                log.getUser() != null ? log.getUser().getRole().name() : null,
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getDetails(),
                log.getIpAddress(),
                log.getCreatedAt()
        );
    }
}