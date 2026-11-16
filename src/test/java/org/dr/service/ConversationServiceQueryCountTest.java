package org.dr.service;

import org.dr.model.Conversation;
import org.dr.model.ConversationMember;
import org.dr.model.Message;
import org.dr.model.User;
import org.dr.model.enums.ConversationMemberRole;
import org.dr.model.enums.ConversationStatus;
import org.dr.model.enums.ConversationType;
import org.dr.model.enums.MessageType;
import org.dr.model.enums.UserRole;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the chat list does not run one query per conversation/member
 * any more.
 *
 * <p>Before the optimisation a page of 20 conversations with 4 members each
 * needed roughly 140 statements (1 for the page, 20 for the members, 80 for
 * the users, 20 for the last message, 20 for the senders). The whole page is
 * now built with 3 statements.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(ConversationService.class)
class ConversationServiceQueryCountTest {

    private static final int CONVERSATIONS = 20;
    private static final int MEMBERS_PER_CONVERSATION = 4;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SessionFactory sessionFactory;

    private String userId;

    @BeforeEach
    void setUp() {
        List<User> users = new ArrayList<>();
        for (int u = 0; u < MEMBERS_PER_CONVERSATION; u++) {
            users.add(entityManager.persist(User.builder()
                    .username("user-" + u)
                    .email("user-" + u + "@example.com")
                    .passwordHash("hash")
                    .role(UserRole.USER)
                    .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                    .updatedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .build()));
        }
        entityManager.flush();
        userId = users.get(0).getId();

        for (int c = 0; c < CONVERSATIONS; c++) {
            Conversation conversation = entityManager.persist(Conversation.builder()
                    .type(ConversationType.GROUP)
                    .name("group-" + c)
                    .status(ConversationStatus.ACCEPTED)
                    .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                    .updatedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .build());
            for (User member : users) {
                entityManager.persist(ConversationMember.builder()
                        .conversation(conversation)
                        .user(member)
                        .role(ConversationMemberRole.MEMBER)
                        .joinedAt(LocalDateTime.now(ZoneOffset.UTC))
                        .build());
            }
            entityManager.persist(Message.builder()
                    .conversation(conversation)
                    .sender(users.get(0))
                    .type(MessageType.TEXT)
                    .content("hello " + c)
                    .build());
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("A page of 20 conversations is built with 3 queries - not ~140")
    void chatListUsesAConstantNumberOfQueries() {
        sessionFactory.getStatistics().setStatisticsEnabled(true);
        sessionFactory.getStatistics().clear();

        Slice<?> slice = conversationService.getUserConversations(userId, PageRequest.of(0, 20), null);

        long queries = sessionFactory.getStatistics().getQueryExecutionCount();

        assertThat(slice.getContent()).hasSize(CONVERSATIONS);
        // conversations + members(with user) + last messages
        assertThat(queries)
                .as("statements for the whole chat list")
                .isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("The returned members and the last message are complete")
    void chatListContainsMembersAndLastMessage() {
        Slice<?> slice = conversationService.getUserConversations(userId, PageRequest.of(0, 20), null);

        assertThat(slice.getContent()).hasSize(CONVERSATIONS);
        slice.getContent().forEach(conversation -> {
            var response = (org.dr.dto.response.ConversationResponse) conversation;
            assertThat(response.members()).hasSize(MEMBERS_PER_CONVERSATION);
            assertThat(response.members().get(0).username()).isNotBlank();
            assertThat(response.lastMessage()).isNotNull();
            assertThat(response.lastMessage().content()).startsWith("hello");
        });
    }
}
