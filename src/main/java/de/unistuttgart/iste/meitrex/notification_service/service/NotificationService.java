package de.unistuttgart.iste.meitrex.notification_service.service;

import de.unistuttgart.iste.meitrex.common.event.NotificationEvent;
import de.unistuttgart.iste.meitrex.generated.dto.Notification;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.RecipientStatus;
import de.unistuttgart.iste.meitrex.notification_service.persistence.mapper.NotificationMapper;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.NotificationRecipientRepository;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Notification domain service (no bitmap):
 *  - Consume NotificationEvent and fan out to recipients (UNREAD).
 *  - Query user's notifications (with read flag).
 *  - Mark read operations.
 *
 * Storage:
 *   notification (message row)
 *   notification_recipient (join: one row per (notification,user) with status)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationMapper notificationMapper;

    /** Client to resolve course subscribers when event.userIds is empty. Adjust to your real client. */
    private final CourseServiceClient courseServiceClient;

    /**
     * Handle NotificationEvent from other services (the ONLY event type we keep).
     * Priority: use event.userIds if present; otherwise if courseId present, fetch subscribers from CourseService.
     */
    @Transactional
    public void handleNotificationEvent(final NotificationEvent event) {
        if (event == null) {
            log.warn("handleNotificationEvent called with null event");
            return;
        }
        final UUID courseId = event.getCourseId();

        // 1) Resolve recipients (userIds first; if empty, try course subscribers)
        final List<UUID> recipients = resolveRecipients(event);
        if (recipients.isEmpty()) {
            log.info("No recipients resolved for NotificationEvent (courseId={}, title={}) → skip.", courseId, event.getTitle());
            return;
        }

        // 2) Create the base Notification (courseId may be null for non-course messages)
        final NotificationEntity notification = NotificationEntity.builder()
                .id(null)
                .courseId(courseId) // nullable (e.g., account upgrade, system message)
                .title(Optional.ofNullable(event.getTitle()).filter(s -> !s.isBlank()).orElse("Notification"))
                .description(Optional.ofNullable(event.getMessage()).orElse(""))
                .href(Optional.ofNullable(event.getLink()).orElse("/"))
                .createdAt(Optional.ofNullable(event.getTimestamp()).orElse(OffsetDateTime.now()))
                .build();

        final NotificationEntity saved = notificationRepository.save(notification);

        // 3) Fan out recipients as UNREAD (de-duplicate defensively)
        final List<UUID> uniqueRecipients = new ArrayList<>(new LinkedHashSet<>(recipients));
        final List<NotificationRecipientEntity> rows = uniqueRecipients.stream()
                .map(uid -> NotificationRecipientEntity.builder()
                        .id(null)
                        .userId(uid)
                        .notification(saved)
                        .status(RecipientStatus.UNREAD)
                        .readAt(null)
                        .build())
                .toList();

        try {
            recipientRepository.saveAll(rows);
        } catch (DataIntegrityViolationException ex) {
            // In case of concurrent duplicates (unique(notification_id,user_id)) just log and proceed.
            log.warn("Duplicate recipients while saving (notificationId={}): {}", saved.getId(), ex.getMessage());
        }

        log.info("Notification(id={}) stored for {} recipients (courseId={})", saved.getId(), uniqueRecipients.size(), courseId);
    }

    /**
     * Query notifications for a user, newest-first, with read flag on DTO.
     */
    @Transactional(readOnly = true)
    public List<Notification> getNotificationsForUser(final UUID userId) {
        if (userId == null) return List.of();

        return recipientRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(rec -> {
                    final Notification dto = notificationMapper.entityToDto(rec.getNotification());
                    dto.setRead(rec.getStatus() == RecipientStatus.READ);
                    return dto;
                })
                .toList();
    }

    /**
     * Mark a single notification as READ for a user.
     * If you have a direct repository method, prefer that; here we filter the user's list.
     */
    @Transactional
    public void markRead(final UUID notificationId, final UUID userId) {
        if (notificationId == null || userId == null) return;

        recipientRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(r -> notificationId.equals(r.getNotification().getId()))
                .findFirst()
                .ifPresent(r -> {
                    if (r.getStatus() != RecipientStatus.READ) {
                        r.setStatus(RecipientStatus.READ);
                        r.setReadAt(OffsetDateTime.now());
                        recipientRepository.save(r);
                    }
                });
    }

    /**
     * Mark all notifications as READ for the given user.
     * If you added a bulk update in repository, call it; otherwise do simple loop.
     */
    @Transactional
    public void markAllRead(final UUID userId) {
        if (userId == null) return;

        // Fast path if you created a bulk update:
        // recipientRepository.markAllRead(userId);

        // Fallback: load & update
        recipientRepository.findAllByUserIdOrderByCreatedAtDesc(userId).forEach(r -> {
            if (r.getStatus() != RecipientStatus.READ) {
                r.setStatus(RecipientStatus.READ);
                r.setReadAt(OffsetDateTime.now());
                recipientRepository.save(r);
            }
        });
    }

    // ---------- helpers ----------

    /**
     * Resolve recipients with priority:
     *  1) If event.userIds not empty → use them.
     *  2) Else if event.courseId not null → query CourseService for subscribers.
     *  3) Else → empty.
     */
    private List<UUID> resolveRecipients(final NotificationEvent event) {
        final List<UUID> userIds = event.getUserIds();
        if (userIds != null && !userIds.isEmpty()) {
            return userIds;
        }
        final UUID courseId = event.getCourseId();
        if (courseId != null) {
            try {
                final List<UUID> fromCourse = courseServiceClient.getUserIdsSubscribedToCourse(courseId);
                return (fromCourse != null) ? fromCourse : List.of();
            } catch (Exception ex) {
                log.error("Failed to fetch subscribers for courseId={}, ex={}", courseId, ex.getMessage());
                return List.of();
            }
        }
        return List.of();
    }

    // ---------- SPI (adapt to your real client bean) ----------

    /** Replace with your actual CourseService client interface/bean. */
    public interface CourseServiceClient {
        List<UUID> getUserIdsSubscribedToCourse(UUID courseId);
    }
}
