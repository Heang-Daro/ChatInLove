package org.dr.model;

import jakarta.persistence.*;
import lombok.*;
import org.dr.model.enums.CallSessionStatus;
import org.dr.model.enums.CallType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks an active group call.
 * Active participant IDs are stored so the call card can show who is in the call.
 */
@Entity
@Table(name = "group_call_sessions",
        indexes = @Index(name = "idx_gcall_conv_status", columnList = "conversation_id, status"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupCallSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(name = "caller_id", nullable = false)
    private String callerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallType callType;

    /** User IDs currently in the call. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "group_call_participants",
            joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "user_id")
    @Builder.Default
    private Set<String> activeParticipantIds = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
