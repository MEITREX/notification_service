package de.unistuttgart.iste.meitrex.notification_service.controller;

import de.unistuttgart.iste.meitrex.generated.dto.NotificationData;
import de.unistuttgart.iste.meitrex.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

/**
 * GraphQL controller exposing queries, mutations and subscriptions for notifications.
 */
@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Returns all notifications for a user excluding DO_NOT_NOTIFY.
     *
     * @param userId user id
     * @return list of NotificationData
     */
    @QueryMapping(name = "notifications")
    public List<NotificationData> getNotifications(@Argument final UUID userId) {
        return notificationService.getNotificationsForUser(userId);
    }

    /**
     * Returns unread count for a user.
     *
     * @param userId user id
     * @return unread count
     */
    @QueryMapping(name = "countUnread")
    public int countUnread(@Argument final UUID userId) {
        return notificationService.countUnread(userId);
    }

    /**
     * Marks all unread notifications as read for a user.
     *
     * @param userId user id
     * @return affected rows
     */
    @MutationMapping
    public int markAllRead(@Argument final UUID userId) {
        return notificationService.markAllRead(userId);
    }

    /**
     * Marks a single notification as read for a user.
     *
     * @param userId user id
     * @param notificationId notification id
     * @return 0 or 1
     */
    @MutationMapping
    public int markOneRead(@Argument final UUID userId,
                           @Argument final UUID notificationId) {
        return notificationService.markOneRead(userId, notificationId);
    }

    /**
     * Subscribes to newly added notifications for a user.
     *
     * @param userId user id
     * @return publisher emitting NotificationData
     */
    @SubscriptionMapping
    public Publisher<NotificationData> notificationAdded(@Argument final UUID userId) {
        return notificationService.notificationAddedStream(userId);
    }
}
