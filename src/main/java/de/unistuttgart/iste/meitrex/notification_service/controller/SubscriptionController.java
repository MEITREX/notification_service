package de.unistuttgart.iste.meitrex.notification_service.controller;


import de.unistuttgart.iste.meitrex.common.event.NotificationEvent;
import de.unistuttgart.iste.meitrex.notification_service.service.NotificationService;
import io.dapr.Topic;
import io.dapr.client.domain.CloudEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
@Slf4j
@RestController
@RequiredArgsConstructor
public class SubscriptionController {
    private final NotificationService notificationService;

    @Topic(name = "notification-event", pubsubName = "gits")
    @PostMapping(path = "/notification-event-pubsub")
    public Mono<Void> handleNotificationEvent(@RequestBody CloudEvent<NotificationEvent> cloudEvent,
                                              @RequestHeader Map<String, String> headers) {
        return Mono.fromRunnable(() -> {
            NotificationEvent event = cloudEvent.getData();
            log.info("Received notification-event: {}", event);
            notificationService.handleNotificationEvent(event);
        });
    }
}
