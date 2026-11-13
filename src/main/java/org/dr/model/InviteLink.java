package org.dr.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "invite_links", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"conversation_id", "token"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = true)
    private LocalDateTime expiresAt;

    @Column(nullable = true)
    private Integer maxUses;

    @Column(nullable = false)
    @Builder.Default
    private Integer usesCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        if (expiresAt == null) return false;
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isFull() {
        if (maxUses == null) return false;
        return usesCount >= maxUses;
    }

    public boolean isValid() {
        return !revoked && !isExpired() && !isFull();
    }
}
