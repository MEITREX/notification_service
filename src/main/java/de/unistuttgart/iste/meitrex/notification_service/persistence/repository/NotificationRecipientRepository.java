package de.unistuttgart.iste.meitrex.notification_service.persistence.repository;

import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.RecipientStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipientEntity, UUID> {

    /** All notifications for a user, newest first, with the owning Notification eagerly loaded. */
    @Query("""
           SELECT r FROM NotificationRecipient r
           JOIN FETCH r.notification n
           WHERE r.userId = :userId
           ORDER BY n.createdAt DESC
           """)
    List<NotificationRecipientEntity> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    boolean existsByNotification_IdAndUserId(UUID notificationId, UUID userId);

    long countByUserIdAndStatus(UUID userId, RecipientStatus status);

    @Modifying
    @Query("UPDATE NotificationRecipient r SET r.status = 'READ', r.readAt = CURRENT_TIMESTAMP WHERE r.userId = :userId AND r.status = 'UNREAD'")
    int markAllRead(@Param("userId") UUID userId);
}
