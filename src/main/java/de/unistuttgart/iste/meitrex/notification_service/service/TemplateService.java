package de.unistuttgart.iste.meitrex.notification_service.service;

import de.unistuttgart.iste.meitrex.generated.dto.Template;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.TemplateEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.mapper.TemplateMapper;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository notification_serviceRepository;
    private final TemplateMapper notification_serviceMapper;

    public List<Template> getAllTemplates() {
        List<TemplateEntity> notification_services = notification_serviceRepository.findAll();
        return notification_services.stream().map(notification_serviceMapper::entityToDto).toList();
    }

}
