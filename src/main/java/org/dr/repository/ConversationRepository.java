package org.dr.repository;

import org.dr.model.Conversation;
import org.dr.model.enums.ConversationStatus;
import org.dr.model.enums.ConversationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {
    Optional<Conversation> findByPrivateKey(String privateKey);

    @Query("SELECT c FROM Conversation c JOIN c.members m WHERE m.user.id = :userId AND m.leftAt IS NULL AND (c.status = org.dr.model.enums.ConversationStatus.ACCEPTED OR c.status IS NULL) ORDER BY c.lastMessageAt DESC")
    Page<Conversation> findActiveConversationsForUser(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT DISTINCT c FROM Conversation c JOIN c.members m WHERE c.type = org.dr.model.enums.ConversationType.PRIVATE AND c.status = org.dr.model.enums.ConversationStatus.PENDING AND m.user.id = :userId AND m.user.id <> c.requesterId AND m.leftAt IS NULL ORDER BY c.createdAt DESC")
    Page<Conversation> findPendingRequestsForRecipient(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT c FROM Conversation c WHERE c.type = :type AND c.id = :id")
    Optional<Conversation> findByIdAndType(@Param("id") String id, @Param("type") ConversationType type);
}
