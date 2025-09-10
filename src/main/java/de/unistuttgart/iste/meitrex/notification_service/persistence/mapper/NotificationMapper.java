package de.unistuttgart.iste.meitrex.notification_service.persistence.mapper;

import de.unistuttgart.iste.meitrex.generated.dto.NotificationData;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationEntity;
import org.springframework.stereotype.Component;

/**
 * Maps persistence entities to GraphQL DTOs.
 */
@Component
public class NotificationMapper {

    /**
     * Maps NotificationEntity to NotificationData. The 'read' flag is filled by service layer.
     *
     * @param entity notification entity
     * @return NotificationData dto
     */
    public NotificationData entityToDto(final NotificationEntity entity) {
        if (entity == null) return null;

        final NotificationData dto = new NotificationData();
        dto.setId(entity.getId());
        dto.setCourseId(entity.getCourseId());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setHref(entity.getHref());
        dto.setCreatedAt(entity.getCreatedAt());
        // dto.setRead(...) is set by caller (depends on recipient status)
        return dto;
    }
}
