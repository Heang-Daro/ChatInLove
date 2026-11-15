package org.dr.model;

import jakarta.persistence.*;
import lombok.*;
import org.dr.model.enums.CallSessionStatus;
import org.dr.model.enums.CallType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Persists private 1-to-1 call state so missed/offline calls can be
 * recovered when the receiver comes online within the ring window.
 */
@Entity
@Table(name = "call_sessions",
        indexes = {
            @Index(name = "idx_call_receiver_status", columnList = "receiver_id, status"),
            @Index(name = "idx_call_expires", columnList = "expires_at")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "caller_id", nullable = false)
    private String callerId;

    @Column(name = "receiver_id", nullable = false)
    private String receiverId;

    /** The full SDP offer so the callee can answer after reconnecting. */
    @Column(name = "sdp_offer", columnDefinition = "TEXT")
    private String sdpOffer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallType callType;

    /** Ring window — after this time the call is auto-marked MISSED. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
        if (expiresAt == null) {
            expiresAt = createdAt.plusSeconds(45);
        }
    }
}
