package de.unistuttgart.iste.meitrex.notification_service.persistence.mapper;

import de.unistuttgart.iste.meitrex.generated.dto.Template;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.TemplateEntity;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TemplateMapper {

    private final ModelMapper modelMapper;

    public Template entityToDto(TemplateEntity notification_serviceEntity) {
        // add specific mapping here if needed
        return modelMapper.map(notification_serviceEntity, Template.class);
    }

    public TemplateEntity dtoToEntity(Template notification_service) {
        // add specific mapping here if needed
        return modelMapper.map(notification_service, TemplateEntity.class);
    }
}
