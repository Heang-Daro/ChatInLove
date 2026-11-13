package org.dr.repository;

import org.dr.model.DeviceSession;
import org.dr.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceSessionRepository extends JpaRepository<DeviceSession, String> {
    List<DeviceSession> findByUserAndRevokedFalse(User user);

    List<DeviceSession> findByUserAndRevokedFalseAndExpiresAtAfter(User user, LocalDateTime now);

    Optional<DeviceSession> findByUserAndIdAndRevokedFalse(User user, String sessionId);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
