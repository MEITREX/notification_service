package de.unistuttgart.iste.meitrex.notification_service.persistence.repository;

import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

}

