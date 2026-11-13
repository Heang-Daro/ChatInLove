package org.dr.repository;

import org.dr.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId AND m.deletedAt IS NULL AND m.createdAt < :beforeTime ORDER BY m.createdAt DESC")
    Page<Message> findByConversationIdBefore(
            @Param("conversationId") String conversationId,
            @Param("beforeTime") LocalDateTime beforeTime,
            Pageable pageable
    );
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId AND m.deletedAt IS NULL ORDER BY m.createdAt DESC")
    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(@Param("conversationId") String conversationId);
    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(String conversationId);
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId AND m.deletedAt IS NULL ORDER BY m.createdAt DESC")
    Page<Message> findByConversationId(@Param("conversationId") String conversationId, Pageable pageable);

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