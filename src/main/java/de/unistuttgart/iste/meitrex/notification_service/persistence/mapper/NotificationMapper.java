package de.unistuttgart.iste.meitrex.notification_service.persistence.mapper;

import de.unistuttgart.iste.meitrex.generated.dto.*;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationEntity;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    private final ModelMapper modelMapper;

    public NotificationMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public Notification entityToDto(NotificationEntity entity) {
        return modelMapper.map(entity, Notification.class);
    }
}
