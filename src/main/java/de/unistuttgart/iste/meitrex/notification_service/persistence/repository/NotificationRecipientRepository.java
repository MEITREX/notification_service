// NotificationRecipientRepository.java
package de.unistuttgart.iste.meitrex.notification_service.persistence.repository;

import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity.RecipientStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipientEntity, UUID> {

    @Query("""
        SELECT r FROM NotificationRecipientEntity r
        JOIN FETCH r.notification n
        WHERE r.userId = :userId AND r.status <> :excluded
        ORDER BY n.createdAt DESC
    """)
    List<NotificationRecipientEntity> findAllByUserIdAndStatusNotOrderByCreatedAtDesc(
            @Param("userId") UUID userId,
            @Param("excluded") RecipientStatus excluded
    );

    @Query("""
        SELECT COUNT(r) FROM NotificationRecipientEntity r
        WHERE r.userId = :userId AND r.status = :status
    """)
    long countByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") RecipientStatus status);

    /** Keep for unread badge on the frontend. */
    default long countUnread(UUID userId) {
        return countByUserIdAndStatus(userId, RecipientStatus.UNREAD);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE NotificationRecipientEntity r
        SET r.status = :newStatus, r.readAt = CURRENT_TIMESTAMP
        WHERE r.userId = :userId AND r.status = :oldStatus
    """)
    int updateStatusForUser(
            @Param("userId") UUID userId,
            @Param("oldStatus") RecipientStatus oldStatus,
            @Param("newStatus") RecipientStatus newStatus
    );

    /** Bulk mark all unread as read for a user. */
    default int markAllRead(UUID userId) {
        return updateStatusForUser(userId, RecipientStatus.UNREAD, RecipientStatus.READ);
    }

    /** Mark a single notification as read for a user, regardless of current status. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE NotificationRecipientEntity r
        SET r.status = 'READ', r.readAt = CURRENT_TIMESTAMP
        WHERE r.userId = :userId AND r.notification.id = :notificationId
    """)
    int markOneRead(@Param("userId") UUID userId,
                    @Param("notificationId") UUID notificationId);
}
