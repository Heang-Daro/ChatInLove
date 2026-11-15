package org.dr.repository;

import org.dr.model.GroupCallSession;
import org.dr.model.enums.CallSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupCallSessionRepository extends JpaRepository<GroupCallSession, String> {

    Optional<GroupCallSession> findFirstByConversationIdAndStatusOrderByCreatedAtDesc(
            String conversationId, CallSessionStatus status);

    List<GroupCallSession> findByConversationIdAndStatus(
            String conversationId, CallSessionStatus status);
}
