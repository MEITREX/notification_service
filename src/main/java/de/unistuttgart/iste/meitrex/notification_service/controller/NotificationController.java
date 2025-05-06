package de.unistuttgart.iste.meitrex.notification_service.controller;

import de.unistuttgart.iste.meitrex.generated.dto.Template;
import de.unistuttgart.iste.meitrex.notification_service.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final TemplateService notificationService;

    @QueryMapping
    public List<Template> notification_services() {
        log.info("Request for all notification_services");

        return notificationService.getAllTemplates();
    }
}
