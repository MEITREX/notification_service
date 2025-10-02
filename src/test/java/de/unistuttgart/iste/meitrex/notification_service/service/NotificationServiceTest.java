package de.unistuttgart.iste.meitrex.notification_service.service;

import de.unistuttgart.iste.meitrex.common.event.NotificationEvent;
import de.unistuttgart.iste.meitrex.common.event.ServerSource;
import de.unistuttgart.iste.meitrex.generated.dto.NotificationData;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity.RecipientStatus;
import de.unistuttgart.iste.meitrex.notification_service.persistence.mapper.NotificationMapper;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.NotificationRecipientRepository;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.NotificationRepository;
import de.unistuttgart.iste.meitrex.user_service.client.SettingsServiceClient;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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

    private NotificationEntity entity(UUID id, String title, String desc, String href) {
        return NotificationEntity.builder()
                .id(id).title(title).description(desc).href(href)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void countUnread_delegates() {
        UUID uid = UUID.randomUUID();
        when(recipientRepository.countUnread(uid)).thenReturn(5);
        assertThat(service.countUnread(uid)).isEqualTo(5);
        verify(recipientRepository).countUnread(uid);
        verifyNoInteractions(settingsServiceClient, courseServiceClient);
    }

    @Test
    void getNotificationsForUser_mapsReadFlag() {
        UUID uid = UUID.randomUUID();
        UUID n1 = UUID.randomUUID();
        UUID n2 = UUID.randomUUID();

        var e1 = entity(n1, "T1", "D1", "/a");
        var e2 = entity(n2, "T2", "D2", "/b");

        var r1 = NotificationRecipientEntity.builder().userId(uid).notification(e1).status(RecipientStatus.UNREAD).build();
        var r2 = NotificationRecipientEntity.builder().userId(uid).notification(e2).status(RecipientStatus.READ).build();

        when(recipientRepository.findAllByUserIdAndStatusNotOrderByCreatedAtDesc(uid, RecipientStatus.DO_NOT_NOTIFY))
                .thenReturn(List.of(r1, r2));

        when(notificationMapper.entityToDto(e1)).thenAnswer(inv -> {
            NotificationData d = new NotificationData();
            d.setId(e1.getId()); d.setTitle(e1.getTitle()); d.setDescription(e1.getDescription());
            d.setHref(e1.getHref()); d.setCreatedAt(e1.getCreatedAt());
            return d;
        });
        when(notificationMapper.entityToDto(e2)).thenAnswer(inv -> {
            NotificationData d = new NotificationData();
            d.setId(e2.getId()); d.setTitle(e2.getTitle()); d.setDescription(e2.getDescription());
            d.setHref(e2.getHref()); d.setCreatedAt(e2.getCreatedAt());
            return d;
        });

        var out = service.getNotificationsForUser(uid);
        assertThat(out).hasSize(2);
        var byId = new HashMap<UUID, NotificationData>();
        out.forEach(x -> byId.put(x.getId(), x));
        assertThat(byId.get(n1).getRead()).isFalse();
        assertThat(byId.get(n2).getRead()).isTrue();

        verifyNoInteractions(settingsServiceClient, courseServiceClient);
    }

    @Test
    void markAllRead_delegates() {
        UUID uid = UUID.randomUUID();
        when(recipientRepository.markAllRead(uid)).thenReturn(9);
        assertThat(service.markAllRead(uid)).isEqualTo(9);
        verify(recipientRepository).markAllRead(uid);
        verifyNoInteractions(settingsServiceClient, courseServiceClient);
    }

    @Test
    void markOneRead_delegates() {
        UUID uid = UUID.randomUUID(); UUID nid = UUID.randomUUID();
        when(recipientRepository.markOneRead(uid, nid)).thenReturn(1);
        assertThat(service.markOneRead(uid, nid)).isEqualTo(1);
        verify(recipientRepository).markOneRead(uid, nid);
        verifyNoInteractions(settingsServiceClient, courseServiceClient);
    }

    @Test
    void notificationAddedStream_withExplicitUserIds_doesNotCallClients_andEmits() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        Publisher<NotificationData> p1 = service.notificationAddedStream(u1);
        Publisher<NotificationData> p2 = service.notificationAddedStream(u2);

        var receivedU1 = new CopyOnWriteArrayList<NotificationData>();
        var receivedU2 = new CopyOnWriteArrayList<NotificationData>();

        p1.subscribe(new org.reactivestreams.Subscriber<>() {
            public void onSubscribe(org.reactivestreams.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(NotificationData nd) { receivedU1.add(nd); }
            public void onError(Throwable t) {}
            public void onComplete() {}
        });
        p2.subscribe(new org.reactivestreams.Subscriber<>() {
            public void onSubscribe(org.reactivestreams.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(NotificationData nd) { receivedU2.add(nd); }
            public void onError(Throwable t) {}
            public void onComplete() {}
        });

        var event = new NotificationEvent();
        event.setUserIds(List.of(u1, u2));
        event.setServerSource(ServerSource.MEDIA);
        event.setTitle("T"); event.setMessage("M"); event.setLink("/x");

        var saved = entity(UUID.randomUUID(), "T", "M", "/x");
        when(notificationRepository.save(any())).thenReturn(saved);
        when(notificationMapper.entityToDto(saved)).thenAnswer(inv -> {
            NotificationData d = new NotificationData();
            d.setId(saved.getId());
            d.setTitle(saved.getTitle());
            d.setDescription(saved.getDescription());
            d.setHref(saved.getHref());
            d.setCreatedAt(saved.getCreatedAt());
            d.setRead(false);
            return d;
        });

        service.handleNotificationEvent(event);

        assertThat(receivedU1).hasSize(1);
        assertThat(receivedU2).hasSize(1);

        verify(recipientRepository).saveAll(argThat((ArgumentMatcher<Iterable<NotificationRecipientEntity>>) rows -> {
            int cnt=0; int unread=0;
            for (NotificationRecipientEntity r : rows) { cnt++; if (r.getStatus()==RecipientStatus.UNREAD) unread++; }
            return cnt==2 && unread==2;
        }));
        verifyNoInteractions(courseServiceClient);
    }

    @Test
    void handleNotificationEvent_nullEvent_noop() {
        service.handleNotificationEvent(null);
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(recipientRepository);
        verifyNoInteractions(settingsServiceClient, courseServiceClient);
    }

    @Test
    void deleteAll_cleansOrphans() {
        UUID uid = UUID.randomUUID();
        UUID n1 = UUID.randomUUID();
        UUID n2 = UUID.randomUUID();

        when(recipientRepository.findNotificationIdsByUserId(uid)).thenReturn(List.of(n1, n2));
        when(recipientRepository.deleteAllByUserId(uid)).thenReturn(2);
        when(recipientRepository.countByNotificationId(n1)).thenReturn(0L);
        when(recipientRepository.countByNotificationId(n2)).thenReturn(3L);

        int affected = service.deleteAll(uid);
        assertThat(affected).isEqualTo(2);
        verify(notificationRepository).deleteById(n1);
        verify(notificationRepository, never()).deleteById(n2);
        verifyNoInteractions(settingsServiceClient, courseServiceClient);
    }

    @Test
    void deleteOne_cleansOrphanWhenLastRecipient() {
        UUID uid = UUID.randomUUID();
        UUID nid = UUID.randomUUID();
        when(recipientRepository.deleteByUserIdAndNotificationId(uid, nid)).thenReturn(1);
        when(recipientRepository.countByNotificationId(nid)).thenReturn(0L);

        int affected = service.deleteOne(uid, nid);
        assertThat(affected).isEqualTo(1);
        verify(notificationRepository).deleteById(nid);
        verifyNoInteractions(settingsServiceClient, courseServiceClient);
    }

    @Test
    void deleteOne_noopWhenParamsNull() {
        assertThat(service.deleteOne(null, UUID.randomUUID())).isEqualTo(0);
        assertThat(service.deleteOne(UUID.randomUUID(), null)).isEqualTo(0);
        verifyNoInteractions(recipientRepository);
        verifyNoInteractions(settingsServiceClient, courseServiceClient);
    }
}
