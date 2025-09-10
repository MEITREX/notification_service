package de.unistuttgart.iste.meitrex.notification_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_recipient",
        uniqueConstraints = @UniqueConstraint(columnNames = {"notification_id","user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationRecipientEntity {

    public enum RecipientStatus { UNREAD, READ, DO_NOT_NOTIFY }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private NotificationEntity notification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecipientStatus status;

    @Column(name = "read_at")
    private OffsetDateTime readAt;
}