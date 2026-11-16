package org.dr.repository;

import org.dr.model.Conversation;
import org.dr.model.ConversationMember;
import org.dr.model.Message;
import org.dr.model.User;
import org.dr.model.enums.ConversationMemberRole;
import org.dr.model.enums.ConversationStatus;
import org.dr.model.enums.ConversationType;
import org.dr.model.enums.MessageType;
import org.dr.model.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the repositories that were rewritten to remove the
 * N+1 selects and the COUNT(*) queries.
 *
 * <p>They verify that the DATA is exactly the same as before - only the
 * number of statements changed.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MessageAndConversationRepositoryTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User alice;
    private User bob;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        alice = user("alice", "alice@example.com");
        bob = user("bob", "bob@example.com");
        entityManager.flush();

        conversation = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .name(null)
                .status(ConversationStatus.ACCEPTED)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .updatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        entityManager.persist(conversation);

        member(conversation, alice, ConversationMemberRole.MEMBER);
        member(conversation, bob, ConversationMemberRole.MEMBER);
        entityManager.flush();

        message("first message", alice, conversation, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        message("second message", bob, conversation, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        message("third message", alice, conversation, LocalDateTime.now(ZoneOffset.UTC));
        entityManager.flush();
        entityManager.clear();
    }

    private User user(String username, String email) {
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash("hash")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .updatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        return entityManager.persist(user);
    }

    private void member(Conversation conversation, User user, ConversationMemberRole role) {
        entityManager.persist(ConversationMember.builder()
                .conversation(conversation)
                .user(user)
                .role(role)
                .joinedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());
    }

    private void message(String content, User sender, Conversation conversation, LocalDateTime createdAt) {
        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .type(MessageType.TEXT)
                .content(content)
                .build();
        entityManager.persist(message);
        entityManager.flush();
        // @PrePersist always sets createdAt to "now", so the timestamp that
        // defines the order has to be written explicitly.
        entityManager.getEntityManager()
                .createQuery("UPDATE Message m SET m.createdAt = :ts WHERE m.id = :id")
                .setParameter("ts", createdAt)
                .setParameter("id", message.getId())
                .executeUpdate();
    }

    @Test
    @DisplayName("The chat list returns a Slice (no COUNT) with the same conversations")
    void chatListReturnsSlice() {
        Slice<Conversation> slice = conversationRepository
                .findActiveConversationsForUser(alice.getId(), PageRequest.of(0, 20));

        assertThat(slice.getContent()).hasSize(1);
        assertThat(slice.getContent().get(0).getId()).isEqualTo(conversation.getId());
        // A Slice does not know the total number of elements - that is exactly
        // why the COUNT(*) query is not executed any more.
        assertThat(slice.hasNext()).isFalse();
        assertThat(slice.getNumberOfElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("The delta filter of the chat list only returns changed conversations")
    void chatListSinceFilter() {
        Slice<Conversation> slice = conversationRepository
                .findActiveConversationsForUserChangedSince(
                        alice.getId(),
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1),
                        PageRequest.of(0, 20));
        assertThat(slice.getContent()).isEmpty();

        Slice<Conversation> older = conversationRepository
                .findActiveConversationsForUserChangedSince(
                        alice.getId(),
                        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(60),
                        PageRequest.of(0, 20));
        assertThat(older.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("All members of a page are loaded with their user in one query")
    void membersAreLoadedWithTheirUser() {
        List<ConversationMember> members = conversationMemberRepository
                .findActiveMembersWithUser(List.of(conversation.getId()));

        assertThat(members).hasSize(2);
        assertThat(members).extracting(m -> m.getUser().getUsername())
                .containsExactlyInAnyOrder("alice", "bob");
        // The user is initialised - no lazy loading exception.
        assertThat(members).allSatisfy(m -> assertThat(m.getUser().getEmail()).isNotBlank());
    }

    @Test
    @DisplayName("The last message of every conversation is returned in one query")
    void lastMessagesOfConversations() {
        List<Message> lastMessages = messageRepository
                .findLastMessagesOfConversations(List.of(conversation.getId()));

        assertThat(lastMessages).hasSize(1);
        assertThat(lastMessages.get(0).getContent()).isEqualTo("third message");
        assertThat(lastMessages.get(0).getSender().getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("A page of messages contains the sender without an extra query")
    void messagePageContainsTheSender() {
        Slice<Message> page = messageRepository
                .findByConversationId(conversation.getId(), PageRequest.of(0, 50));

        assertThat(page.getContent()).hasSize(3);
        // newest first
        assertThat(page.getContent().get(0).getContent()).isEqualTo("third message");
        assertThat(page.getContent().get(2).getContent()).isEqualTo("first message");
        // The sender proxy is initialised (JOIN FETCH).
        assertThat(page.getContent()).allSatisfy(m ->
                assertThat(m.getSender().getUsername()).isNotBlank());
    }

    @Test
    @DisplayName("The delta sync only returns the messages after the given cursor")
    void deltaSyncReturnsOnlyNewerMessages() {
        // The sequence id is assigned by the database (Flyway migration), so
        // the test sets it explicitly.
        entityManager.getEntityManager().createQuery(
                        "UPDATE Message m SET m.sequenceId = 1 WHERE m.content = 'first message'")
                .executeUpdate();
        entityManager.getEntityManager().createQuery(
                        "UPDATE Message m SET m.sequenceId = 2 WHERE m.content = 'second message'")
                .executeUpdate();
        entityManager.getEntityManager().createQuery(
                        "UPDATE Message m SET m.sequenceId = 3 WHERE m.content = 'third message'")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        Slice<Message> delta = messageRepository
                .findByConversationIdAfterSequence(conversation.getId(), 1L, PageRequest.of(0, 200));

        assertThat(delta.getContent()).hasSize(2);
        // oldest first
        assertThat(delta.getContent().get(0).getContent()).isEqualTo("second message");
        assertThat(delta.getContent().get(1).getContent()).isEqualTo("third message");
    }

    @Test
    @DisplayName("The keyset pagination returns the messages before a timestamp")
    void keysetPagination() {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(7);
        Slice<Message> older = messageRepository
                .findByConversationIdBefore(conversation.getId(), before, PageRequest.of(0, 50));

        assertThat(older.getContent()).hasSize(1);
        assertThat(older.getContent().get(0).getContent()).isEqualTo("first message");
    }
}
