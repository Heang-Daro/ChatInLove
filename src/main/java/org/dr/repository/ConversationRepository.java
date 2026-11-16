package org.dr.repository;

import org.dr.model.Conversation;
import org.dr.model.enums.ConversationStatus;
import org.dr.model.enums.ConversationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {
    Optional<Conversation> findByPrivateKey(String privateKey);

    /**
     * All conversations of a user, newest activity first.
     *
     * <p>{@link Slice} instead of {@link Page}: a Slice never runs the
     * {@code COUNT(*)} query, which was one of the most expensive statements
     * of every chat-list request.
     */
    @Query("SELECT c FROM Conversation c JOIN c.members m WHERE m.user.id = :userId AND m.leftAt IS NULL AND (c.status = org.dr.model.enums.ConversationStatus.ACCEPTED OR c.status IS NULL) ORDER BY c.lastMessageAt DESC")
    Slice<Conversation> findActiveConversationsForUser(@Param("userId") String userId, Pageable pageable);

    /**
     * Delta sync of the chat list: only the conversations that changed since
     * the given timestamp.
     *
     * <p>Used by the clients with {@code ?since=<ISO-8601>} on the first page.
     */
    @Query("SELECT c FROM Conversation c JOIN c.members m WHERE m.user.id = :userId AND m.leftAt IS NULL AND (c.status = org.dr.model.enums.ConversationStatus.ACCEPTED OR c.status IS NULL) AND COALESCE(c.lastMessageAt, c.createdAt) >= :since ORDER BY c.lastMessageAt DESC")
    Slice<Conversation> findActiveConversationsForUserChangedSince(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    @Query("SELECT DISTINCT c FROM Conversation c JOIN c.members m WHERE c.type = org.dr.model.enums.ConversationType.PRIVATE AND c.status = org.dr.model.enums.ConversationStatus.PENDING AND m.user.id = :userId AND m.user.id <> c.requesterId AND m.leftAt IS NULL ORDER BY c.createdAt DESC")
    Slice<Conversation> findPendingRequestsForRecipient(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT c FROM Conversation c WHERE c.type = :type AND c.id = :id")
    Optional<Conversation> findByIdAndType(@Param("id") String id, @Param("type") ConversationType type);

    /**
     * Targeted update of the activity timestamp of a conversation.
     *
     * <p>Writing the whole entity instead would re-check every column of the
     * aggregate (and its lazy collections) on every single message.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Conversation c SET c.lastMessageAt = :timestamp, c.updatedAt = :timestamp WHERE c.id = :conversationId")
    void updateLastMessageAt(
            @Param("conversationId") String conversationId,
            @Param("timestamp") LocalDateTime timestamp);
}
