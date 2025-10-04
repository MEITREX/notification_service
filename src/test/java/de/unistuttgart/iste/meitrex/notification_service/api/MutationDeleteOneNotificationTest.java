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
class MutationDeleteOneNotificationTest {

    @InjectCurrentUserHeader
    LoggedInUser currentUser;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    NotificationRecipientRepository recipientRepository;

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
    void shouldDeleteOne(GraphQlTester graphQlTester) {
        UUID uid = currentUser.getId();

        NotificationEntity n1 = notificationRepository.save(NotificationEntity.builder()
                .title("T1")
                .description("M1")
                .href("/l1")
                .createdAt(OffsetDateTime.now())
                .build());
        NotificationEntity n2 = notificationRepository.save(NotificationEntity.builder()
                .title("T2")
                .description("M2")
                .href("/l2")
                .createdAt(OffsetDateTime.now())
                .build());

        recipientRepository.save(NotificationRecipientEntity.builder().notification(n1).userId(uid).status(RecipientStatus.UNREAD).build());
        recipientRepository.save(NotificationRecipientEntity.builder().notification(n2).userId(uid).status(RecipientStatus.UNREAD).build());

        graphQlTester.document("mutation($uid: UUID!, $nid: UUID!){ deleteOneNotification(userId:$uid, notificationId:$nid) }")
                .variable("uid", uid)
                .variable("nid", n1.getId())
                .execute()
                .path("deleteOneNotification").entity(Integer.class).isEqualTo(1);

        List<String> titles = graphQlTester.document("query($uid: UUID!){ notifications(userId:$uid){ title } }")
                .variable("uid", uid)
                .execute()
                .path("notifications[*].title").entityList(String.class).get();

        assertThat(titles).contains("T2");
        assertThat(titles).doesNotContain("T1");
    }
}
