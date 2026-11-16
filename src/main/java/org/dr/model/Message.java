package org.dr.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.dr.model.enums.MessageType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User sender;

    @Column(nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = true)
    private LocalDateTime editedAt;

    @Column(nullable = true)
    private LocalDateTime deletedAt;  // Soft delete

    @Column(nullable = true)
    private String relatedCallId;  // For CALL type messages

    /**
     * Monotonic server-side cursor used for delta sync.
     *
     * <p>The value is assigned by the database (see migration
     * {@code V2__performance_indexes_and_delta_sync.sql}), therefore the column
     * is not insertable/updatable by Hibernate — otherwise the column would be
     * written as NULL and the default would never fire.
     */
    @Column(name = "sequence_id", insertable = false, updatable = false)
    private Long sequenceId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}