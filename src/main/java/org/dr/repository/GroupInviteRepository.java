package org.dr.repository;

import org.dr.model.GroupInvite;
import org.dr.model.enums.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupInviteRepository extends JpaRepository<GroupInvite, String> {
    Optional<GroupInvite> findByConversationIdAndInvitedUserId(String conversationId, String invitedUserId);

    List<GroupInvite> findByInvitedUserIdAndStatus(String invitedUserId, InviteStatus status);

    List<GroupInvite> findByConversationIdAndStatus(String conversationId, InviteStatus status);

    boolean existsByConversationIdAndInvitedUserIdAndStatus(String conversationId, String invitedUserId, InviteStatus status);
}
