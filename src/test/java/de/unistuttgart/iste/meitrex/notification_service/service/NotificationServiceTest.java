package de.unistuttgart.iste.meitrex.notification_service.service;

import de.unistuttgart.iste.meitrex.common.event.NotificationEvent;
import de.unistuttgart.iste.meitrex.common.event.ServerSource;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.course_service.exception.CourseServiceConnectionException;
import de.unistuttgart.iste.meitrex.generated.dto.NotificationData;
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
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.reactivestreams.Publisher;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationRecipientRepository recipientRepository;
    @Mock NotificationMapper notificationMapper;
    @Mock CourseServiceClient courseServiceClient;
    @Mock SettingsServiceClient settingsServiceClient;

    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                notificationRepository, recipientRepository, notificationMapper,
                courseServiceClient, settingsServiceClient
        );
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            NotificationEntity in = inv.getArgument(0);
            return NotificationEntity.builder()
                    .id(UUID.randomUUID())
                    .title(in.getTitle())
                    .description(in.getDescription())
                    .href(in.getHref())
                    .createdAt(OffsetDateTime.now())
                    .build();
        });
        when(notificationMapper.entityToDto(any(NotificationEntity.class))).thenAnswer(inv -> {
            NotificationEntity e = inv.getArgument(0);
            NotificationData d = new NotificationData();
            d.setId(e.getId());
            d.setTitle(e.getTitle());
            d.setDescription(e.getDescription());
            d.setHref(e.getHref());
            d.setCreatedAt(e.getCreatedAt());
            d.setRead(false);
            return d;
        });
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

        var out = service.getNotificationsForUser(uid);
        assertThat(out).hasSize(2);
        var byId = new HashMap<UUID, NotificationData>();
        out.forEach(x -> byId.put(x.getId(), x));
        assertThat(byId.get(n1).getRead()).isFalse();
        assertThat(byId.get(n2).getRead()).isTrue();
    }

    @Test
    void markAllRead_delegates() {
        UUID uid = UUID.randomUUID();
        when(recipientRepository.markAllRead(uid)).thenReturn(9);
        assertThat(service.markAllRead(uid)).isEqualTo(9);
        verify(recipientRepository).markAllRead(uid);
    }

    @Test
    void markOneRead_delegates() {
        UUID uid = UUID.randomUUID(); UUID nid = UUID.randomUUID();
        when(recipientRepository.markOneRead(uid, nid)).thenReturn(1);
        assertThat(service.markOneRead(uid, nid)).isEqualTo(1);
        verify(recipientRepository).markOneRead(uid, nid);
    }

    @Test
    void notificationAddedStream_withExplicitUserIds_emits() {
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

        service.handleNotificationEvent(event);

        assertThat(receivedU1).hasSize(1);
        assertThat(receivedU2).hasSize(1);

        verify(recipientRepository).saveAll(argThat((ArgumentMatcher<Iterable<NotificationRecipientEntity>>) rows -> {
            int cnt=0; int unread=0;
            for (NotificationRecipientEntity r : rows) { cnt++; if (r.getStatus()==RecipientStatus.UNREAD) unread++; }
            return cnt==2 && unread==2;
        }));
    }

    @Test
    void notificationAddedStream_multipleEvents_emitInOrder() {
        UUID u = UUID.randomUUID();
        Publisher<NotificationData> p = service.notificationAddedStream(u);
        var got = new CopyOnWriteArrayList<String>();
        p.subscribe(new org.reactivestreams.Subscriber<>() {
            public void onSubscribe(org.reactivestreams.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(NotificationData nd) { got.add(nd.getTitle()); }
            public void onError(Throwable t) {}
            public void onComplete() {}
        });

        var e1 = new NotificationEvent();
        e1.setUserIds(List.of(u)); e1.setServerSource(ServerSource.MEDIA); e1.setTitle("A"); e1.setMessage("a"); e1.setLink("/a");
        var e2 = new NotificationEvent();
        e2.setUserIds(List.of(u)); e2.setServerSource(ServerSource.MEDIA); e2.setTitle("B"); e2.setMessage("b"); e2.setLink("/b");

        service.handleNotificationEvent(e1);
        service.handleNotificationEvent(e2);

        assertThat(got).containsExactly("A", "B");
    }

    @Test
    void handleNotificationEvent_nullEvent_noop() {
        service.handleNotificationEvent(null);
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(recipientRepository);
    }

    @Test
    void handleNotificationEvent_noRecipients_noop() {
        var event = new NotificationEvent();
        event.setUserIds(Collections.emptyList());
        event.setCourseId(null);
        event.setServerSource(ServerSource.MEDIA);
        event.setTitle("t"); event.setMessage("m"); event.setLink("/x");

        service.handleNotificationEvent(event);

        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(recipientRepository);
    }

    @Test
    void handleNotificationEvent_withCourseId_noMembers_noop() throws CourseServiceConnectionException {
        UUID courseId = UUID.randomUUID();
        doReturn(Collections.emptyList()).when(courseServiceClient).queryMembershipsInCourse(courseId);

        var event = new NotificationEvent();
        event.setCourseId(courseId);
        event.setServerSource(ServerSource.CONTENT);
        event.setTitle("T"); event.setMessage("M"); event.setLink("/x");

        service.handleNotificationEvent(event);

        verifyNoInteractions(recipientRepository);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void handleNotificationEvent_withCourseId_broadcastsAndEmits() throws CourseServiceConnectionException {
        UUID courseId = UUID.randomUUID();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        Publisher<NotificationData> p1 = service.notificationAddedStream(u1);
        Publisher<NotificationData> p2 = service.notificationAddedStream(u2);

        var got1 = new CopyOnWriteArrayList<NotificationData>();
        var got2 = new CopyOnWriteArrayList<NotificationData>();

        p1.subscribe(new org.reactivestreams.Subscriber<>() {
            public void onSubscribe(org.reactivestreams.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(NotificationData nd) { got1.add(nd); }
            public void onError(Throwable t) {}
            public void onComplete() {}
        });
        p2.subscribe(new org.reactivestreams.Subscriber<>() {
            public void onSubscribe(org.reactivestreams.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(NotificationData nd) { got2.add(nd); }
            public void onError(Throwable t) {}
            public void onComplete() {}
        });

        var m1 = new de.unistuttgart.iste.meitrex.generated.dto.CourseMembership();
        m1.setUserId(u1);
        m1.setRole(de.unistuttgart.iste.meitrex.generated.dto.UserRoleInCourse.STUDENT);
        var m2 = new de.unistuttgart.iste.meitrex.generated.dto.CourseMembership();
        m2.setUserId(u2);
        m2.setRole(de.unistuttgart.iste.meitrex.generated.dto.UserRoleInCourse.TUTOR);
        doReturn(List.of(m1, m2)).when(courseServiceClient).queryMembershipsInCourse(courseId);

        var event = new NotificationEvent();
        event.setCourseId(courseId);
        event.setServerSource(ServerSource.CONTENT);
        event.setTitle("T"); event.setMessage("M"); event.setLink("/x");

        service.handleNotificationEvent(event);

        assertThat(got1).hasSize(1);
        assertThat(got2).hasSize(1);

        verify(recipientRepository).saveAll(argThat((ArgumentMatcher<Iterable<NotificationRecipientEntity>>) rows -> {
            int cnt=0, unread=0;
            for (NotificationRecipientEntity r : rows) { cnt++; if (r.getStatus()==RecipientStatus.UNREAD) unread++; }
            return cnt==2 && unread==2;
        }));
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
    }

    @Test
    void deleteOne_noopWhenParamsNull() {
        assertThat(service.deleteOne(null, UUID.randomUUID())).isEqualTo(0);
        assertThat(service.deleteOne(UUID.randomUUID(), null)).isEqualTo(0);
        verifyNoInteractions(recipientRepository);
    }
}
