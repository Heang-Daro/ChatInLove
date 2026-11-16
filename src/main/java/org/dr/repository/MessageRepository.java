package org.dr.repository;

import org.dr.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    /**
     * Oldest page of the messages of a conversation (newest first).
     *
     * <p>{@link Slice} instead of {@link Page}: a Slice fetches one row more
     * than requested and never runs the expensive {@code COUNT(*)} query.
     *
     * <p>The sender is fetched with a JOIN FETCH — without it every single
     * row would trigger its own SELECT for the sender proxy (N+1).
     */
    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.conversation.id = :conversationId AND m.deletedAt IS NULL ORDER BY m.createdAt DESC")
    Slice<Message> findByConversationId(
            @Param("conversationId") String conversationId,
            Pageable pageable);

    /**
     * Messages of a conversation that are older than {@code beforeTime}
     * (keyset pagination used by the "load older messages" button).
     */
    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.conversation.id = :conversationId AND m.deletedAt IS NULL AND m.createdAt < :beforeTime ORDER BY m.createdAt DESC")
    Slice<Message> findByConversationIdBefore(
            @Param("conversationId") String conversationId,
            @Param("beforeTime") LocalDateTime beforeTime,
            Pageable pageable);

    /**
     * Delta sync: every message of the conversation whose sequence id is
     * greater than {@code afterSequenceId}, oldest first.
     *
     * <p>This is what the clients call with {@code ?after=<sequenceId>} when
     * they reconnect - they only fetch what they missed instead of the newest
     * N messages.
     */
    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.conversation.id = :conversationId AND m.deletedAt IS NULL AND m.sequenceId > :afterSequenceId ORDER BY m.sequenceId ASC")
    Slice<Message> findByConversationIdAfterSequence(
            @Param("conversationId") String conversationId,
            @Param("afterSequenceId") Long afterSequenceId,
            Pageable pageable);

    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.conversation.id = :conversationId AND m.deletedAt IS NULL ORDER BY m.createdAt DESC")
    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(@Param("conversationId") String conversationId);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(String conversationId);

    /**
     * The newest message of every given conversation — one single query for a
     * whole page of conversations (no N+1, no COUNT).
     */
    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.conversation.id IN :conversationIds AND m.deletedAt IS NULL AND m.createdAt = (SELECT MAX(m2.createdAt) FROM Message m2 WHERE m2.conversation.id = m.conversation.id AND m2.deletedAt IS NULL)")
    List<Message> findLastMessagesOfConversations(
            @Param("conversationIds") List<String> conversationIds);

    List<Message> findByRelatedCallId(String callId);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.isRead = true WHERE m.id = :messageId")
    void markAsRead(@Param("messageId") String messageId);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.isRead = true WHERE m.conversation.id = :conversationId")
    void markAllAsRead(@Param("conversationId") String conversationId);
}
