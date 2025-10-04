package de.unistuttgart.iste.meitrex.notification_service.testconfig;

import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.user_service.client.SettingsServiceClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MockDownstreamClientsConfiguration {

    @Bean
    @Primary
    public CourseServiceClient courseServiceClient() {
        return Mockito.mock(CourseServiceClient.class);
    }

    @Bean
    @Primary
    public SettingsServiceClient settingsServiceClient() {
        return Mockito.mock(SettingsServiceClient.class);
    }
}
