package de.unistuttgart.iste.meitrex.notification_service.api;

import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.testutil.TablesToDelete;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity;
import de.unistuttgart.iste.meitrex.notification_service.persistence.entity.NotificationRecipientEntity.RecipientStatus;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.NotificationRecipientRepository;
import de.unistuttgart.iste.meitrex.notification_service.persistence.repository.NotificationRepository;
import de.unistuttgart.iste.meitrex.notification_service.testconfig.MockDownstreamClientsConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@GraphQlApiTest
@Import(MockDownstreamClientsConfiguration.class)
@TablesToDelete({"notification_recipient","notification"})
class MutationDeleteAllNotificationsTest {

    @InjectCurrentUserHeader
    LoggedInUser currentUser;

    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationRecipientRepository recipientRepository;

    @BeforeEach
    void initUser() {
        if (currentUser == null) {
            currentUser = LoggedInUser.builder()
                    .id(UUID.randomUUID())
                    .userName("test")
                    .firstName("test")
                    .lastName("test")
                    .nickname("test")
                    .courseMemberships(List.of())
                    .realmRoles(Set.of())
                    .build();
        }
    }

    @Test
    void deleteAllNotifications_removesOnlyMyRecipients_andCleansOrphans(GraphQlTester graphQlTester) {
        UUID me = currentUser.getId();
        UUID other = UUID.randomUUID();

        NotificationEntity onlyMine = notificationRepository.save(NotificationEntity.builder()
                .title("T1")
                .description("M1")
                .href("/l1")
                .createdAt(OffsetDateTime.now())
                .build());
        NotificationEntity shared = notificationRepository.save(NotificationEntity.builder()
                .title("T2")
                .description("M2")
                .href("/l2")
                .createdAt(OffsetDateTime.now())
                .build());

        recipientRepository.save(NotificationRecipientEntity.builder().notification(onlyMine).userId(me).status(RecipientStatus.UNREAD).build());
        recipientRepository.save(NotificationRecipientEntity.builder().notification(shared).userId(me).status(RecipientStatus.UNREAD).build());
        recipientRepository.save(NotificationRecipientEntity.builder().notification(shared).userId(other).status(RecipientStatus.UNREAD).build());

        String doc = "mutation($uid: UUID!){ deleteAllNotifications(userId:$uid) }";

        graphQlTester.document(doc)
                .variable("uid", me)
                .execute()
                .path("deleteAllNotifications").entity(Integer.class).isEqualTo(2);

        List<NotificationRecipientEntity> remainingRecipients = recipientRepository.findAll();
        assertThat(remainingRecipients).hasSize(1);
        assertThat(remainingRecipients.get(0).getUserId()).isEqualTo(other);

        assertThat(notificationRepository.findById(onlyMine.getId())).isNotPresent();
        assertThat(notificationRepository.findById(shared.getId())).isPresent();
    }
}
