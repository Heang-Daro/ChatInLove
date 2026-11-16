package org.dr.repository;

import org.dr.model.ConversationMember;
import org.dr.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, String> {
    Optional<ConversationMember> findByConversationIdAndUserId(String conversationId, String userId);

    List<ConversationMember> findByConversationIdAndLeftAtIsNull(String conversationId);

    List<ConversationMember> findByUserIdAndLeftAtIsNull(String userId);

    boolean existsByConversationIdAndUserIdAndLeftAtIsNull(String conversationId, String userId);

    /**
     * All active members of the given conversations - with their user.
     *
     * <p>The {@code JOIN FETCH cm.user} is essential: without it every member
     * of every conversation would trigger its own SELECT for the user proxy
     * (the classic N+1). One query now replaces dozens of them.
     */
    @Query("SELECT cm FROM ConversationMember cm JOIN FETCH cm.user WHERE cm.conversation.id IN :conversationIds AND cm.leftAt IS NULL")
    List<ConversationMember> findActiveMembersWithUser(
            @Param("conversationIds") List<String> conversationIds);

    /**
     * The active members of a single conversation - with their user (no N+1).
     */
    @Query("SELECT cm FROM ConversationMember cm JOIN FETCH cm.user WHERE cm.conversation.id = :conversationId AND cm.leftAt IS NULL")
    List<ConversationMember> findActiveMembersWithUserOfConversation(
            @Param("conversationId") String conversationId);
}
