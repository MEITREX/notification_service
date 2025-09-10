package de.unistuttgart.iste.meitrex.notification_service;

import de.unistuttgart.iste.meitrex.common.event.NotificationEvent;
import de.unistuttgart.iste.meitrex.generated.dto.Notification;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.mapper.NotificationMapper;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.NotificationRecipientRepository;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.NotificationRepository;
import de.unistuttgart.iste.meitrex.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationRecipientRepository recipientRepository;
    @Mock private NotificationMapper notificationMapper;
    @Mock private NotificationService.CourseServiceClient courseServiceClient;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void handleEvent_usesExplicitUserIds_withoutCallingCourseService() {
        // Arrange: event with explicit userIds
        UUID courseId = UUID.randomUUID();
        List<UUID> userIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        NotificationEvent event = NotificationEvent.builder()
                .courseId(courseId)
                .userIds(userIds)                 // non-empty → should NOT call CourseService
                .title("Title")
                .message("Message")
                .link("/somewhere")
                .timestamp(OffsetDateTime.now())
                .build();

        NotificationEntity saved = NotificationEntity.builder()
                .id(UUID.randomUUID())
                .courseId(courseId)
                .title("Title")
                .description("Message")
                .href("/somewhere")
                .createdAt(OffsetDateTime.now())
                .build();

        when(notificationRepository.save(any(NotificationEntity.class))).thenReturn(saved);
        when(recipientRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        notificationService.handleNotificationEvent(event);

        // Assert: never resolve from CourseService
        verify(courseServiceClient, never()).getUserIdsSubscribedToCourse(any());

        // Assert: recipients saved exactly as passed
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationRecipientEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(recipientRepository).saveAll(captor.capture());

        List<NotificationRecipientEntity> rows = captor.getValue();
        assertThat(rows, hasSize(userIds.size()));
        assertTrue(rows.stream().allMatch(r -> r.getStatus() == RecipientStatus.UNREAD));
        assertThat(rows.stream().map(NotificationRecipientEntity::getUserId).toList(),
                containsInAnyOrder(userIds.toArray(new UUID[0])));
        assertTrue(rows.stream().allMatch(r -> r.getNotification().getId().equals(saved.getId())));
    }

    @Test
    void handleEvent_whenUserIdsEmpty_fetchFromCourseService_andDeduplicate() {
        // Arrange: userIds empty → must query CourseService by courseId
        UUID courseId = UUID.randomUUID();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        NotificationEvent event = NotificationEvent.builder()
                .courseId(courseId)
                .userIds(Collections.emptyList())  // empty → trigger lookup
                .title("T2")
                .message("M2")
                .link("/x")
                .build();

        // CourseService returns duplicates
        when(courseServiceClient.getUserIdsSubscribedToCourse(courseId))
                .thenReturn(Arrays.asList(u1, u2, u1));

        NotificationEntity saved = NotificationEntity.builder()
                .id(UUID.randomUUID())
                .courseId(courseId)
                .title("T2")
                .description("M2")
                .href("/x")
                .createdAt(OffsetDateTime.now())
                .build();
        when(notificationRepository.save(any(NotificationEntity.class))).thenReturn(saved);
        when(recipientRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        notificationService.handleNotificationEvent(event);

        // Assert: deduplicated to size 2
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationRecipientEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(recipientRepository).saveAll(captor.capture());
        List<NotificationRecipientEntity> rows = captor.getValue();

        assertThat(rows, hasSize(2));
        assertThat(rows.stream().map(NotificationRecipientEntity::getUserId).toList(),
                containsInAnyOrder(u1, u2));
    }

    @Test
    void handleEvent_noRecipients_doesNothing() {
        // Arrange: userIds empty AND courseId null → resolves to empty recipients
        NotificationEvent event = NotificationEvent.builder()
                .userIds(Collections.emptyList())
                .title("T3")
                .message("M3")
                .link("/z")
                .build();

        // Act
        notificationService.handleNotificationEvent(event);

        // Assert: nothing persisted
        verify(notificationRepository, never()).save(any());
        verify(recipientRepository, never()).saveAll(anyList());
    }

    @Test
    void getNotificationsForUser_mapsReadFlagFromRecipientStatus() {
        // Arrange: one UNREAD and one READ for the same user
        UUID userId = UUID.randomUUID();

        NotificationEntity n1 = NotificationEntity.builder()
                .id(UUID.randomUUID())
                .title("N1").description("D1").href("/1").createdAt(OffsetDateTime.now())
                .build();
        NotificationEntity n2 = NotificationEntity.builder()
                .id(UUID.randomUUID())
                .title("N2").description("D2").href("/2").createdAt(OffsetDateTime.now())
                .build();

        NotificationRecipientEntity r1 = NotificationRecipientEntity.builder()
                .id(UUID.randomUUID()).userId(userId).notification(n1).status(RecipientStatus.UNREAD).build();
        NotificationRecipientEntity r2 = NotificationRecipientEntity.builder()
                .id(UUID.randomUUID()).userId(userId).notification(n2).status(RecipientStatus.READ).build();

        when(recipientRepository.findAllByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(r1, r2));

        // Map entity -> dto (we assert read flag below)
        when(notificationMapper.entityToDto(n1)).thenReturn(
                Notification.builder()
                        .setId(n1.getId()).setTitle(n1.getTitle()).setDescription(n1.getDescription())
                        .setHref(n1.getHref()).setCreatedAt(n1.getCreatedAt()).build()
        );
        when(notificationMapper.entityToDto(n2)).thenReturn(
                Notification.builder()
                        .setId(n2.getId()).setTitle(n2.getTitle()).setDescription(n2.getDescription())
                        .setHref(n2.getHref()).setCreatedAt(n2.getCreatedAt()).build()
        );

        // Act
        List<Notification> out = notificationService.getNotificationsForUser(userId);

        // Assert
        assertThat(out, hasSize(2));
        assertEquals(n1.getId(), out.get(0).getId());
        assertEquals(n2.getId(), out.get(1).getId());
        assertFalse(out.get(0).getRead()); // UNREAD -> false
        assertTrue(out.get(1).getRead());  // READ   -> true
    }

    @Test
    void markRead_updatesRecipientToRead() {
        // Arrange: one unread row for user
        UUID userId = UUID.randomUUID();

        NotificationEntity n = NotificationEntity.builder()
                .id(UUID.randomUUID())
                .title("N").description("D").href("/n").createdAt(OffsetDateTime.now())
                .build();
        NotificationRecipientEntity rec = NotificationRecipientEntity.builder()
                .id(UUID.randomUUID()).userId(userId).notification(n).status(RecipientStatus.UNREAD).build();

        when(recipientRepository.findAllByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(rec));

        // Act
        notificationService.markRead(n.getId(), userId);

        // Assert
        assertEquals(RecipientStatus.READ, rec.getStatus());
        assertNotNull(rec.getReadAt());
        verify(recipientRepository).save(rec);
    }
}
