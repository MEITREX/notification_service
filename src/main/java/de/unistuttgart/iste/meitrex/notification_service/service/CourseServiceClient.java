package de.unistuttgart.iste.meitrex.notification_service.service;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.dapr.client.domain.HttpExtension;
import io.dapr.utils.TypeRef;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CourseServiceClient {

    private final DaprClient daprClient = new DaprClientBuilder().build();

    public List<UUID> getUserIdsSubscribedToCourse(UUID courseId) {
        UUID[] response = daprClient.invokeMethod(
                "course-service", // Dapr service name
                "api/course/" + courseId + "/subscribers", // HTTP GET path
                null, // No request body for GET
                HttpExtension.GET,
                UUID[].class // Response type is an array
        ).block();

        return response != null ? Arrays.asList(response) : List.of();
    }
}
