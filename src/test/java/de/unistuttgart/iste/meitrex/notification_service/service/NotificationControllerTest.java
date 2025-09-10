package de.unistuttgart.iste.meitrex.notification_service.service;


import de.unistuttgart.iste.meitrex.generated.dto.NotificationData;
import de.unistuttgart.iste.meitrex.notification_service.controller.NotificationController;
import de.unistuttgart.iste.meitrex.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock NotificationService service;

    @Test
    void notifications_delegates() {
        var c = new NotificationController(service);
        var uid = UUID.randomUUID();
        when(service.getNotificationsForUser(uid)).thenReturn(List.of(new NotificationData()));
        assertThat(c.getNotifications(uid)).hasSize(1);
        verify(service).getNotificationsForUser(uid);
    }

    @Test
    void countUnread_delegates() {
        var c = new NotificationController(service);
        var uid = UUID.randomUUID();
        when(service.countUnread(uid)).thenReturn(5);
        assertThat(c.countUnread(uid)).isEqualTo(5);
    }

    @Test
    void markAllRead_delegates() {
        var c = new NotificationController(service);
        var uid = UUID.randomUUID();
        when(service.markAllRead(uid)).thenReturn(9);
        assertThat(c.markAllRead(uid)).isEqualTo(9);
    }

    @Test
    void markOneRead_delegates() {
        var c = new NotificationController(service);
        var uid = UUID.randomUUID();
        var nid = UUID.randomUUID();
        when(service.markOneRead(uid, nid)).thenReturn(1);
        assertThat(c.markOneRead(uid, nid)).isEqualTo(1);
    }
}