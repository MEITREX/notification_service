package de.unistuttgart.iste.meitrex.notification_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Recipient join-table: one row per (notification, user). */
@Entity(name = "NotificationRecipient")
@Table(name = "notification_recipient",
        uniqueConstraints = @UniqueConstraint(columnNames = {"notification_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRecipientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The user who receives this notification. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Owning notification. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private NotificationEntity notification;

    /** Per-recipient status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecipientStatus status;

    /** When the user read this notification (nullable). */
    @Column(name = "read_at")
    private OffsetDateTime readAt;
}
