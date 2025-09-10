package de.unistuttgart.iste.meitrex.notification_service.service;

import de.unistuttgart.iste.meitrex.common.event.NotificationEvent;
import de.unistuttgart.iste.meitrex.common.event.ServerSource;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.generated.dto.NotificationData;
import de.unistuttgart.iste.meitrex.generated.dto.Settings;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity.RecipientStatus;
import de.unistuttgart.iste.meitrex.notification_service.persistence.mapper.NotificationMapper;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.NotificationRecipientRepository;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.NotificationRepository;
import de.unistuttgart.iste.meitrex.user_service.client.SettingsServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationRecipientRepository recipientRepository;
    @Mock NotificationMapper notificationMapper;
    @Mock CourseServiceClient courseServiceClient;
    @Mock SettingsServiceClient settingsServiceClient;

    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, recipientRepository, notificationMapper,
                courseServiceClient, settingsServiceClient);
    }

    // ---------- helpers ----------
    private NotificationEntity stubSavedEntity() {
        return NotificationEntity.builder()
                .id(UUID.randomUUID())
                .title("T")
                .description("D")
                .href("/")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private de.unistuttgart.iste.meitrex.generated.dto.Notification settingsNotif(Boolean lecture, Boolean gamif) {
        var n = new de.unistuttgart.iste.meitrex.generated.dto.Notification();
        n.setLecture(lecture);
        n.setGamification(gamif);
        return n;
    }

    private Settings settings(Boolean lecture, Boolean gamif) {
        var s = new Settings();
        s.setNotification(settingsNotif(lecture, gamif));
        return s;
    }

    // ---------- tests ----------

    @Test
    void countUnread_delegatesToRepository() {
        UUID uid = UUID.randomUUID();
        when(recipientRepository.countUnread(uid)).thenReturn(3);
        assertThat(service.countUnread(uid)).isEqualTo(3);
        verify(recipientRepository).countUnread(uid);
    }

    @Test
    void markAllRead_delegates() {
        UUID uid = UUID.randomUUID();
        when(recipientRepository.markAllRead(uid)).thenReturn(7);
        assertThat(service.markAllRead(uid)).isEqualTo(7);
    }

    @Test
    void markOneRead_delegates() {
        UUID uid = UUID.randomUUID();
        UUID nid = UUID.randomUUID();
        when(recipientRepository.markOneRead(uid, nid)).thenReturn(1);
        assertThat(service.markOneRead(uid, nid)).isEqualTo(1);
    }

    @Test
    void getNotificationsForUser_mapsReadFlagFromRecipientStatus() {
        var uid = UUID.randomUUID();
        var entity = stubSavedEntity();

        var r1 = new NotificationRecipientEntity();
        r1.setUserId(uid);
        r1.setNotification(entity);
        r1.setStatus(RecipientStatus.UNREAD);

        var r2 = new NotificationRecipientEntity();
        r2.setUserId(uid);
        r2.setNotification(entity);
        r2.setStatus(RecipientStatus.READ);

        when(recipientRepository.findAllByUserIdAndStatusNotOrderByCreatedAtDesc(uid, RecipientStatus.DO_NOT_NOTIFY))
                .thenReturn(List.of(r1, r2));

        when(notificationMapper.entityToDto(any(NotificationEntity.class)))
                .thenAnswer(inv -> new NotificationData());

        var list = service.getNotificationsForUser(uid);

        assertThat(list).hasSize(2);
        long readCount   = list.stream().filter(n -> Boolean.TRUE.equals(n.getRead())).count();
        long unreadCount = list.stream().filter(n -> !Boolean.TRUE.equals(n.getRead())).count();
        assertThat(readCount).isEqualTo(1);
        assertThat(unreadCount).isEqualTo(1);
    }


    @Test
    void handleNotificationEvent_publishesOnlyForUsersAllowedBySettings() throws Exception{
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        var event = new NotificationEvent();
        event.setUserIds(List.of(u1, u2));
        event.setServerSource(ServerSource.CONTENT); // lecture category
        event.setTitle("Hello"); event.setMessage("M"); event.setLink("/");

        when(settingsServiceClient.queryUserSettings(u1)).thenReturn(settings(true, false));
        when(settingsServiceClient.queryUserSettings(u2)).thenReturn(settings(false, false));

        var saved = stubSavedEntity();
        when(notificationRepository.save(any())).thenReturn(saved);
        when(notificationMapper.entityToDto(saved)).thenReturn(new NotificationData());

        var receivedU1 = new CopyOnWriteArrayList<NotificationData>();
        var receivedU2 = new CopyOnWriteArrayList<NotificationData>();
        Disposable sub1 = Flux.from(service.notificationAddedStream(u1)).subscribe(receivedU1::add);
        Disposable sub2 = Flux.from(service.notificationAddedStream(u2)).subscribe(receivedU2::add);

        service.handleNotificationEvent(event);

        assertThat(receivedU1).hasSize(1);
        assertThat(receivedU2).isEmpty();

        sub1.dispose(); sub2.dispose();
        verify(recipientRepository).saveAll(argThat(list -> {
            int count = 0;
            for (Object ignored : list) count++;
            return count == 2;
        }));

    }
}
