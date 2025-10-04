package de.unistuttgart.iste.meitrex.notification_service.controller;

import de.unistuttgart.iste.meitrex.common.event.NotificationEvent;
import de.unistuttgart.iste.meitrex.notification_service.service.NotificationService;
import io.dapr.client.domain.CloudEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    NotificationService service;

    @Test
    void handleNotificationEvent_delegatesToService_andCompletes() {
        SubscriptionController c = new SubscriptionController(service);

        NotificationEvent e = new NotificationEvent();
        e.setTitle("T"); e.setMessage("M"); e.setLink("/x");

        CloudEvent<NotificationEvent> ce = new CloudEvent<>();
        ce.setData(e);

        Mono<Void> out = c.handleNotificationEvent(ce, Map.of());
        out.block();

        verify(service).handleNotificationEvent(e);
        verifyNoMoreInteractions(service);
        assertThat(out).isNotNull();
    }
}
