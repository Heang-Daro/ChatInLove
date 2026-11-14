package org.dr.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dr.dto.request.CreateGroupConversationRequest;
import org.dr.dto.request.CreatePrivateConversationRequest;
import org.dr.dto.request.SendMessageRequest;
import org.dr.dto.response.ConversationResponse;
import org.dr.dto.response.MessageResponse;
import org.dr.model.User;
import org.dr.service.ConversationService;
import org.dr.service.MessageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ConversationController {
    private final ConversationService conversationService;
    private final MessageService messageService;

    @GetMapping
    public ResponseEntity<Page<ConversationResponse>> getConversations(@AuthenticationPrincipal User user,
            @PageableDefault(size = 20, sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(conversationService.getUserConversations(user.getId(), pageable));
    }

    @GetMapping("/requests")
    public ResponseEntity<Page<ConversationResponse>> getRequests(@AuthenticationPrincipal User user,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
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

    @GetMapping("/{id}/messages")
    public ResponseEntity<Page<MessageResponse>> getMessages(@PathVariable String id,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(messageService.getConversationMessages(id, user.getId(), before, pageable));
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
