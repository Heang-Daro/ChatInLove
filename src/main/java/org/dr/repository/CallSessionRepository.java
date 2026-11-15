package org.dr.repository;

import org.dr.model.CallSession;
import org.dr.model.enums.CallSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallSessionRepository extends JpaRepository<CallSession, String> {

    /** Find the most recent active ringing call for a receiver. */
    @Query("SELECT c FROM CallSession c WHERE c.receiverId = :receiverId " +
           "AND c.status = 'RINGING' AND c.expiresAt > :now " +
           "ORDER BY c.createdAt DESC")
    List<CallSession> findPendingForReceiver(@Param("receiverId") String receiverId,
                                              @Param("now") LocalDateTime now);

    /** Find by ID and caller for ownership checks. */
    Optional<CallSession> findByIdAndCallerId(String id, String callerId);

    /** Expire all RINGING calls that have passed their ring window. */
    @Modifying
    @Query("UPDATE CallSession c SET c.status = 'MISSED', c.endedAt = :now " +
           "WHERE c.status = 'RINGING' AND c.expiresAt <= :now")
    int expireRingingCalls(@Param("now") LocalDateTime now);
}
