package de.unistuttgart.iste.meitrex.notification_service.config;

import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.user_service.client.SettingsServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures downstream GraphQL clients used by the notification-service.
 * <p>
 * URLs are provided via application.properties or environment variables:
 * <ul>
 *   <li>course_service.url = http://localhost:2001/graphql</li>
 *   <li>user_service.url   = http://localhost:5001/graphql</li>
 * </ul>
 */
@Configuration
public class DownstreamClientsConfiguration {

    @Bean
    public CourseServiceClient courseServiceClient(
            @Value("${course_service.url}") final String courseServiceUrl) {
        final WebClient webClient = WebClient.builder()
                .baseUrl(courseServiceUrl)
                .build();
        final GraphQlClient gql = HttpGraphQlClient.builder(webClient).build();
        return new CourseServiceClient(gql);
    }

    @Bean
    public SettingsServiceClient settingsServiceClient(
            @Value("${user_service.url}") final String userServiceUrl) {
        final WebClient webClient = WebClient.builder()
                .baseUrl(userServiceUrl)
                .build();
        final GraphQlClient gql = HttpGraphQlClient.builder(webClient).build();
        return new SettingsServiceClient(gql);
    }
}
