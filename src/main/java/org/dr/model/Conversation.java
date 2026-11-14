package org.dr.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dr.model.enums.ConversationStatus;
import org.dr.model.enums.ConversationType;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationType type;

    @Column(nullable = true)
    private String name;

    @Column(nullable = true)
    private String description;

    @Column(nullable = true, unique = true)
    private String privateKey;

    /** User who initiated a private chat request; null for groups/legacy chats. */
    @Column(nullable = true)
    private String requesterId;

    /** Nullable so existing rows remain compatible; null is treated as ACCEPTED. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ConversationStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = true)
    private LocalDateTime lastMessageAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConversationMember> members;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Message> messages;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (type == ConversationType.GROUP && status == null) status = ConversationStatus.ACCEPTED;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
