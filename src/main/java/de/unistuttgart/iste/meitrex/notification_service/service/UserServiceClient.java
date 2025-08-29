package de.unistuttgart.iste.meitrex.notification_service.service;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.dapr.client.domain.HttpExtension;
import io.dapr.utils.TypeRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;

@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final DaprClient daprClient = new DaprClientBuilder().build();

    /**
     * Fetches lecture notification preferences for a list of users.
     *
     * @param userIds the list of user UUIDs to query
     * @return a list of booleans indicating whether each user wants lecture notifications
     */
    public List<Boolean> getLectureNotificationPreferences(List<UUID> userIds) {
        return daprClient.invokeMethod(
                "user-service",
                "api/user/lecture-notification-preferences",
                userIds, // request body
                HttpExtension.POST,
                new TypeRef<List<Boolean>>() {}
        ).block();
    }

    /**
     * Calls the user-service to get the number of registered users.
     *
     * @return total user count.
     */
    public int getUserCount() {
        Integer result = daprClient.invokeMethod(
                "user-service",
                "api/user/count",
                null,
                HttpExtension.GET,
                TypeRef.INT
        ).block();

        if (result == null) {
            throw new IllegalStateException("User count response from user-service was null");
        }

        return result;
    }
}
