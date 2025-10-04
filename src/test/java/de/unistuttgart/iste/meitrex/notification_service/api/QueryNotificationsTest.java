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
class QueryNotificationsTest {

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
    void shouldListMyNotifications(GraphQlTester graphQlTester) {
        UUID uid = currentUser.getId();

        NotificationEntity n1 = NotificationEntity.builder()
                .title("T1")
                .description("M1")
                .href("/l1")
                .createdAt(OffsetDateTime.now())
                .build();
        NotificationEntity n2 = NotificationEntity.builder()
                .title("T2")
                .description("M2")
                .href("/l2")
                .createdAt(OffsetDateTime.now())
                .build();
        n1 = notificationRepository.save(n1);
        n2 = notificationRepository.save(n2);

        recipientRepository.save(NotificationRecipientEntity.builder().notification(n1).userId(uid).status(RecipientStatus.UNREAD).build());
        recipientRepository.save(NotificationRecipientEntity.builder().notification(n2).userId(uid).status(RecipientStatus.READ).build());

        Integer unread = graphQlTester.document("query($uid: UUID!){ countUnread(userId:$uid) }")
                .variable("uid", uid)
                .execute()
                .path("countUnread").entity(Integer.class).get();
        assertThat(unread).isEqualTo(1);

        var res = graphQlTester.document("query($uid: UUID!){ notifications(userId:$uid){ title read } }")
                .variable("uid", uid)
                .execute();

        List<String> titles = res.path("notifications[*].title").entityList(String.class).get();
        assertThat(titles).contains("T1", "T2");

        List<Boolean> reads = res.path("notifications[*].read").entityList(Boolean.class).get();
        assertThat(reads).contains(true);
        assertThat(reads).contains(false);
    }
}
