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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void notifyReviewers(Schedule schedule, NotificationType type, String message) {
        List<User> reviewers = userRepository.findAllByRole(UserRole.REVIEWER);
        for (User reviewer : reviewers) {
            Notification notification = Notification.builder()
                    .user(reviewer)
                    .schedule(schedule)
                    .type(type)
                    .message(message)
                    .build();
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void notifyUser(User user, Schedule schedule, NotificationType type, String message) {
        Notification notification = Notification.builder()
                .user(user)
                .schedule(schedule)
                .type(type)
                .message(message)
                .build();
        notificationRepository.save(notification);
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