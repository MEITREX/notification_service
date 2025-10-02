package de.unistuttgart.iste.meitrex.notification_service.persistence.mapper;

import de.unistuttgart.iste.meitrex.generated.dto.NotificationData;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationEntity;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMapperTest {

    @Test
    void entityToDto_mapsFields() {
        NotificationEntity e = NotificationEntity.builder()
                .id(UUID.randomUUID())
                .title("T")
                .description("D")
                .href("/h")
                .createdAt(OffsetDateTime.now())
                .build();

        NotificationMapper mapper = new NotificationMapper();
        NotificationData d = mapper.entityToDto(e);

        assertThat(d.getId()).isEqualTo(e.getId());
        assertThat(d.getTitle()).isEqualTo("T");
        assertThat(d.getDescription()).isEqualTo("D");
        assertThat(d.getHref()).isEqualTo("/h");
        assertThat(d.getCreatedAt()).isEqualTo(e.getCreatedAt());
    }

    @Test
    void entityToDto_handlesNulls() {
        NotificationEntity e = NotificationEntity.builder().build();

        NotificationMapper mapper = new NotificationMapper();
        NotificationData d = mapper.entityToDto(e);

        assertThat(d.getId()).isNull();
        assertThat(d.getTitle()).isNull();
        assertThat(d.getDescription()).isNull();
        assertThat(d.getHref()).isNull();
        assertThat(d.getCreatedAt()).isNull();
    }
}
