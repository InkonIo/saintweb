package com.schedule.app.service;

import com.schedule.app.dto.response.NotificationResponse;
import com.schedule.app.entity.Notification;
import com.schedule.app.entity.Schedule;
import com.schedule.app.entity.User;
import com.schedule.app.enums.NotificationType;
import com.schedule.app.enums.UserRole;
import com.schedule.app.repository.NotificationRepository;
import com.schedule.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Transactional
    public void notifyReviewers(Schedule schedule, NotificationType type, String message) {
        List<User> reviewers = userRepository.findAllByRole(UserRole.REVIEWER);
        for (User reviewer : reviewers) {
            saveNotification(reviewer, schedule, type, message);
            sendEmail(reviewer.getEmail(), subjectFor(type), message);
        }
    }

    @Transactional
    public void notifyUser(User user, Schedule schedule, NotificationType type, String message) {
        saveNotification(user, schedule, type, message);
        sendEmail(user.getEmail(), subjectFor(type), message);

        // если график утверждён — уведомляем и сотрудников этого филиала
        if (type == NotificationType.SCHEDULE_APPROVED) {
            notifyEmployees(schedule, message);
        }
    }

    private void notifyEmployees(Schedule schedule, String message) {
        schedule.getEntries().stream()
                .map(entry -> entry.getEmployee().getUser())
                .filter(user -> user != null)
                .distinct()
                .forEach(user -> {
                    saveNotification(user, schedule, NotificationType.SCHEDULE_APPROVED, message);
                    sendEmail(user.getEmail(), subjectFor(NotificationType.SCHEDULE_APPROVED), message);
                });
    }

    private void saveNotification(User user, Schedule schedule, NotificationType type, String message) {
        Notification notification = Notification.builder()
                .user(user)
                .schedule(schedule)
                .type(type)
                .message(message)
                .build();
        notificationRepository.save(notification);
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(to);
            mail.setSubject(subject);
            mail.setText(text);
            mailSender.send(mail);
        } catch (Exception e) {
            log.warn("Не удалось отправить email на {}: {}", to, e.getMessage());
        }
    }

    private String subjectFor(NotificationType type) {
        return switch (type) {
            case SCHEDULE_SUBMITTED -> "График отправлен на согласование";
            case SCHEDULE_APPROVED  -> "График утверждён";
            case SCHEDULE_REVISION  -> "График возвращён на доработку";
            default                 -> "Уведомление по графику";
        };
    }

    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(n -> new NotificationResponse(
                        n.getId(),
                        n.getType(),
                        n.getMessage(),
                        n.getIsRead(),
                        n.getCreatedAt(),
                        new NotificationResponse.ScheduleRef(n.getSchedule().getId())
                ))
                .toList();
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }
}