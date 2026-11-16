package org.dr.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.dto.request.CreateGroupConversationRequest;
import org.dr.dto.request.CreatePrivateConversationRequest;
import org.dr.dto.request.SendMessageRequest;
import org.dr.dto.response.ConversationResponse;
import org.dr.dto.response.MessageResponse;
import org.dr.model.User;
import org.dr.service.ConversationService;
import org.dr.service.MessageService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin("*")
public class ConversationController {
    private final ConversationService conversationService;
    private final MessageService messageService;

    /**
     * The conversations of the current user.
     *
     * <p>Returns a {@link Slice}: the response contains the same {@code content}
     * array as before plus {@code last}/{@code hasNext}, but the expensive
     * COUNT query is not executed any more.
     *
     * <p>Optional {@code since} (ISO-8601): only the conversations that changed
     * since that timestamp - the delta sync of the clients.
     */
    @GetMapping
    public ResponseEntity<Slice<ConversationResponse>> getConversations(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String since,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                conversationService.getUserConversations(user.getId(), pageable, parseSince(since)));
    }

    /**
     * Parses the optional {@code since} parameter.
     *
     * <p>The clients send an ISO-8601 timestamp with a zone
     * ({@code 2026-11-16T07:00:00.000Z}); a local timestamp without a zone is
     * accepted as well and interpreted as UTC. An unparsable value is ignored
     * (the client then simply receives the full first page).
     */
    static LocalDateTime parseSince(String since) {
        if (since == null || since.isBlank()) return null;
        String value = since.trim();
        try {
            return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
        } catch (Exception ignored) {
            // not an instant - try the other ISO-8601 shapes
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
            // not an offset date time - try a local date time
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            log.warn("Could not parse the 'since' parameter: {}", value);
            return null;
        }
    }

    @GetMapping("/requests")
    public ResponseEntity<Slice<ConversationResponse>> getRequests(@AuthenticationPrincipal User user,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(conversationService.getPendingRequests(user.getId(), pageable));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ConversationResponse> acceptRequest(@PathVariable String id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(conversationService.respondToRequest(id, user.getId(), true));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ConversationResponse> rejectRequest(@PathVariable String id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(conversationService.respondToRequest(id, user.getId(), false));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable String id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(conversationService.getConversation(id, user.getId()));
    }

    @PostMapping("/private")
    public ResponseEntity<ConversationResponse> createPrivateConversation(@AuthenticationPrincipal User user,
            @Valid @RequestBody CreatePrivateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.getOrCreatePrivateConversation(user.getId(), request));
    }

    @PostMapping("/group")
    public ResponseEntity<ConversationResponse> createGroup(@AuthenticationPrincipal User user,
            @Valid @RequestBody CreateGroupConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.createGroupConversation(user.getId(), request));
    }

    @GetMapping("/last/{id}/messages")
    public ResponseEntity<MessageResponse> getLastMessage(@PathVariable String id) {
        return ResponseEntity.ok(messageService.lastMessageByConversation(id));
    }

    /**
     * The messages of a conversation.
     *
     * <p>Optional {@code after} (the sequence id of the last known message):
     * returns only the messages the client missed - the delta sync. Optional
     * {@code before}: keyset pagination for "load older messages".
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<Slice<MessageResponse>> getMessages(@PathVariable String id,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(required = false) Long after,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(
                messageService.getConversationMessages(id, user.getId(), before, after, pageable));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(@PathVariable String id, @AuthenticationPrincipal User user,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.sendMessage(id, user.getId(), request));
    }

    @PutMapping("/messages/{messageId}")
    public ResponseEntity<MessageResponse> editMessage(@PathVariable String messageId,
            @AuthenticationPrincipal User user, @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(messageService.editMessage(messageId, user.getId(), request));
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String messageId, @AuthenticationPrincipal User user) {
        messageService.deleteMessage(messageId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/messages/{messageId}/read")
    public ResponseEntity<Void> markMessageAsRead(@PathVariable String conversationId,
            @PathVariable String messageId, @AuthenticationPrincipal User user) {
        conversationService.markMessageAsRead(conversationId, messageId, user.getId());
        messageService.markMessageAsRead(messageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/read-all")
    public ResponseEntity<Void> markAllMessagesAsRead(@PathVariable String conversationId,
            @AuthenticationPrincipal User user) {
        conversationService.getConversation(conversationId, user.getId());
        messageService.markAllMessagesAsRead(conversationId);
        return ResponseEntity.noContent().build();
    }
}
