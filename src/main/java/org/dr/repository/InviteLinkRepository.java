package org.dr.repository;

import org.dr.model.InviteLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InviteLinkRepository extends JpaRepository<InviteLink, String> {
    Optional<InviteLink> findByTokenAndRevokedFalse(String token);

    List<InviteLink> findByConversationIdAndRevokedFalse(String conversationId);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
