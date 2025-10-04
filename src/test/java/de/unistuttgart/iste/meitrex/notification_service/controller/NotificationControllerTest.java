package de.unistuttgart.iste.meitrex.notification_service.controller;

import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.NotificationData;
import de.unistuttgart.iste.meitrex.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock NotificationService service;
    @Mock LoggedInUser currentUser;

    @Test
    void countUnread_delegates() {
        var c = new NotificationController(service);
        var uid = UUID.randomUUID();
        when(currentUser.getId()).thenReturn(uid);
        when(service.countUnread(uid)).thenReturn(4);
        assertThat(c.countUnread(uid, currentUser)).isEqualTo(4);
    }

    @Test
    void notificationAdded_delegates() {
        var c = new NotificationController(service);
        var uid = UUID.randomUUID();
        when(currentUser.getId()).thenReturn(uid);
        Publisher<NotificationData> p = subscriber -> {};
        when(service.notificationAddedStream(uid)).thenReturn(p);
        assertThat(c.notificationAdded(uid, currentUser)).isSameAs(p);
    }

    @Test
    void deleteAll_delegates() {
        var c = new NotificationController(service);
        var uid = UUID.randomUUID();
        when(currentUser.getId()).thenReturn(uid);
        when(service.deleteAll(uid)).thenReturn(7);
        assertThat(c.deleteAllNotifications(uid, currentUser)).isEqualTo(7);
    }

    @Test
    void deleteOne_delegates() {
        var c = new NotificationController(service);
        var uid = UUID.randomUUID();
        var nid = UUID.randomUUID();
        when(currentUser.getId()).thenReturn(uid);
        when(service.deleteOne(uid, nid)).thenReturn(1);
        assertThat(c.deleteOneNotification(uid, nid, currentUser)).isEqualTo(1);
    }

    @Test
    void markAllRead_delegates() {
        var c = new NotificationController(service);
        var uid = UUID.randomUUID();
        when(currentUser.getId()).thenReturn(uid);
        when(service.markAllRead(uid)).thenReturn(9);
        assertThat(c.markAllRead(uid, currentUser)).isEqualTo(9);
    }

    @Test
    void markOneRead_delegates() {
        var c = new NotificationController(service);
        var uid = UUID.randomUUID();
        var nid = UUID.randomUUID();
        when(currentUser.getId()).thenReturn(uid);
        when(service.markOneRead(uid, nid)).thenReturn(1);
        assertThat(c.markOneRead(uid, nid, currentUser)).isEqualTo(1);
    }
}
