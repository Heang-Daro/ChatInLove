package org.dr.repository;

import org.dr.model.ConversationMember;
import org.dr.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, String> {
    Optional<ConversationMember> findByConversationIdAndUserId(String conversationId, String userId);

    List<ConversationMember> findByConversationIdAndLeftAtIsNull(String conversationId);

    List<ConversationMember> findByUserIdAndLeftAtIsNull(String userId);

    boolean existsByConversationIdAndUserIdAndLeftAtIsNull(String conversationId, String userId);
}
