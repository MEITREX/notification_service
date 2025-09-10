package de.unistuttgart.iste.meitrex.notification_service.service;

import de.unistuttgart.iste.meitrex.common.event.NotificationEvent;
import de.unistuttgart.iste.meitrex.common.event.ServerSource;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.generated.dto.CourseMembership;
import de.unistuttgart.iste.meitrex.generated.dto.NotificationData;
import de.unistuttgart.iste.meitrex.generated.dto.Settings;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity.RecipientStatus;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.NotificationRecipientRepository;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.NotificationRepository;
import de.unistuttgart.iste.meitrex.notification_service.persistence.mapper.NotificationMapper;
import de.unistuttgart.iste.meitrex.user_service.client.SettingsServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Sinks;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Core domain service for notifications: event handling, listing, read state, and live streaming.
 * Recipient status is decided per user from their settings and the event's serverSource.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationMapper notificationMapper;

    private final CourseServiceClient courseServiceClient;
    private final SettingsServiceClient settingsServiceClient;

    private final ConcurrentMap<UUID, Sinks.Many<NotificationData>> sinks = new ConcurrentHashMap<>();

    private static final EnumSet<ServerSource> LECTURE_SOURCES =
            EnumSet.of(ServerSource.COURSE, ServerSource.CHAPTER, ServerSource.CONTENT, ServerSource.MEDIA);

    /**
     * Returns a per-user stream for GraphQL subscription.
     *
     * @param userId user id
     * @return publisher emitting NotificationData for this user
     */
    public Publisher<NotificationData> notificationAddedStream(final UUID userId) {
        return sinks.computeIfAbsent(userId, k -> Sinks.many().multicast().onBackpressureBuffer())
                .asFlux();
    }

    /**
     * Publishes a newly created notification to a specific user's stream if present.
     *
     * @param userId user id
     * @param dto    notification dto
     */
    private void publishToUser(final UUID userId, final NotificationData dto) {
        final var sink = sinks.get(userId);
        if (sink != null) {
            sink.tryEmitNext(dto);
        }
    }

    /**
     * Returns all unread notifications count for the given user
     *
     * @param userId user id
     * @return count of unread NotificationData
     */
    @Transactional(readOnly = true)
    public int countUnread(final UUID userId) {
        return recipientRepository.countUnread(userId);
    }

    /**
     * Returns all notifications for the given user excluding DO_NOT_NOTIFY entries.
     * The "read" flag is derived from recipient status.
     *
     * @param userId user id
     * @return list of NotificationData
     */
    @Transactional(readOnly = true)
    public List<NotificationData> getNotificationsForUser(final UUID userId) {
        return recipientRepository
                .findAllByUserIdAndStatusNotOrderByCreatedAtDesc(userId, RecipientStatus.DO_NOT_NOTIFY)
                .stream()
                .map(rec -> {
                    final var dto = notificationMapper.entityToDto(rec.getNotification());
                    dto.setRead(rec.getStatus() != RecipientStatus.UNREAD);
                    return dto;
                })
                .toList();
    }

    /**
     * Marks all unread notifications as read for the given user.
     *
     * @param userId user id
     * @return affected rows
     */
    @Transactional
    public int markAllRead(final UUID userId) {
        return recipientRepository.markAllRead(userId);
    }

    /**
     * Marks a single notification as read for the given user.
     *
     * @param userId user id
     * @param notificationId notification id
     * @return 0 or 1 depending on whether a row was affected
     */
    @Transactional
    public int markOneRead(final UUID userId, final UUID notificationId) {
        return recipientRepository.markOneRead(userId, notificationId);
    }

    /**
     * Handles an incoming NotificationEvent: resolves recipients, fetches settings, persists per-user status,
     * and publishes to subscribers (UNREAD only).
     *
     * @param event incoming event
     */
    @Transactional
    public void handleNotificationEvent(final NotificationEvent event) {
        if (event == null) {
            return;
        }

        final List<UUID> candidates = resolveRecipients(event);
        if (candidates.isEmpty()) {
            log.info("No recipients resolved for event: {}", safeEventTitle(event));
            return;
        }

        final Map<UUID, Settings> settingsByUser = fetchSettingsForUsers(candidates);

        final NotificationEntity saved = notificationRepository.save(
                NotificationEntity.builder()
                        .title(nvl(event.getTitle(), "Notification"))
                        .description(nvl(event.getMessage(), ""))
                        .href(nvl(event.getLink(), "/"))
                        .createdAt(event.getTimestamp() != null ? event.getTimestamp() : OffsetDateTime.now())
                        .build()
        );

        final ServerSource source = event.getServerSource();
        final List<NotificationRecipientEntity> rows = candidates.stream()
                .map(uid -> {
                    final Settings s = settingsByUser.get(uid);
                    final RecipientStatus status = decideStatusForUser(s, source);
                    return NotificationRecipientEntity.builder()
                            .userId(uid)
                            .notification(saved)
                            .status(status)
                            .build();
                })
                .toList();

        recipientRepository.saveAll(rows);

        final NotificationData dto = notificationMapper.entityToDto(saved);
        dto.setRead(false);
        rows.stream()
                .filter(r -> r.getStatus() == RecipientStatus.UNREAD)
                .forEach(r -> publishToUser(r.getUserId(), dto));
    }

    /**
     * Resolves candidate recipients from the event: prefers explicit userIds; otherwise by course memberships.
     *
     * @param event incoming event
     * @return list of userIds
     */
    private List<UUID> resolveRecipients(final NotificationEvent event) {
        if (event.getUserIds() != null && !event.getUserIds().isEmpty()) {
            return event.getUserIds();
        }
        if (event.getCourseId() != null) {
            return resolveRecipientsFromCourse(event.getCourseId());
        }
        return List.of();
    }

    /**
     * Resolves userIds in a course by querying memberships from course-service.
     *
     * @param courseId course id
     * @return distinct user ids or empty list on failure
     */
    private List<UUID> resolveRecipientsFromCourse(final UUID courseId) {
        try {
            final List<CourseMembership> memberships = courseServiceClient.queryMembershipsInCourse(courseId);
            if (memberships == null || memberships.isEmpty()) {
                return List.of();
            }
            return memberships.stream()
                    .map(this::membershipUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (final Exception e) {
            log.warn("Failed to query memberships for courseId={}: {}", courseId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Extracts userId from CourseMembership and converts it to UUID.
     *
     * @param m membership dto
     * @return userId as UUID or null if unparsable
     */
    private UUID membershipUserId(final CourseMembership m) {
        if (m == null) return null;
        try {
            final Object uid = m.getUserId();
            if (uid instanceof UUID u) return u;
            if (uid instanceof String s) return UUID.fromString(s);
        } catch (final Exception ignored) { }
        return null;
    }

    /**
     * Decides the recipient status from user's settings and the server source.
     * - Lecture sources require notification.lecture=true
     * - Non-lecture sources require notification.gamification=true
     * - Missing settings default to UNREAD (allow)
     *
     * @param settings user settings (may be null)
     * @param source   event's server source
     * @return resulting status
     */
    private RecipientStatus decideStatusForUser(final Settings settings, final ServerSource source) {
        if (settings == null || settings.getNotification() == null) {
            return RecipientStatus.UNREAD;
        }

        final de.unistuttgart.iste.meitrex.generated.dto.Notification notificationData =
                settings.getNotification();

        final Boolean lecture = notificationData.getLecture();
        final Boolean gamification = notificationData.getGamification();

        if (source != null && LECTURE_SOURCES.contains(source)) {
            return Boolean.TRUE.equals(lecture) ? RecipientStatus.UNREAD : RecipientStatus.DO_NOT_NOTIFY;
        } else {
            return Boolean.TRUE.equals(gamification) ? RecipientStatus.UNREAD : RecipientStatus.DO_NOT_NOTIFY;
        }
    }

    /**
     * Fetches settings for a batch of users by calling user-service per user.
     * Missing or failed entries are omitted (treated as default allow).
     *
     * @param userIds user ids
     * @return map userId -> Settings
     */
    private Map<UUID, Settings> fetchSettingsForUsers(final List<UUID> userIds) {
        final Map<UUID, Settings> map = new HashMap<>(userIds.size());
        for (UUID uid : userIds) {
            try {
                final Settings s = settingsServiceClient.queryUserSettings(uid);
                if (s != null) {
                    map.put(uid, s);
                }
            } catch (final Exception ex) {
                log.warn("Failed to fetch settings for userId={}: {}", uid, ex.getMessage());
            }
        }
        return map;
    }

    private String nvl(final String s, final String d) {
        return (s == null || s.isBlank()) ? d : s;
    }

    private String safeEventTitle(final NotificationEvent e) {
        return (e != null && e.getTitle() != null) ? e.getTitle() : "<no-title>";
    }
}
