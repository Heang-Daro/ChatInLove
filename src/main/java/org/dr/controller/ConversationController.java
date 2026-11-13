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

	/**
	 * Get all conversations for the current user (paginated, ordered by
	 * lastMessageAt)
	 */
	@GetMapping
	public ResponseEntity<Page<ConversationResponse>> getConversations(@AuthenticationPrincipal User user,
			@PageableDefault(size = 20, sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable) {
		Page<ConversationResponse> response = conversationService.getUserConversations(user.getId(), pageable);
		System.out.println(response.getContent().toString());
		return ResponseEntity.ok(response);
	}

	/**
	 * Get a specific conversation by ID
	 */
	@GetMapping("/{id}")
	public ResponseEntity<ConversationResponse> getConversation(@PathVariable String id,
			@AuthenticationPrincipal User user) {
		ConversationResponse response = conversationService.getConversation(id, user.getId());
		System.out.println(response);
		return ResponseEntity.ok(response);
	}

	/**
	 * Create or get a private conversation with another user
	 */
	@PostMapping("/private")
	public ResponseEntity<ConversationResponse> createPrivateConversation(@AuthenticationPrincipal User user,
			@Valid @RequestBody CreatePrivateConversationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(conversationService.getOrCreatePrivateConversation(user.getId(), request));
	}
	@PostMapping("/group")
	public ResponseEntity<ConversationResponse> createGroup(
	        @AuthenticationPrincipal User principal, // ឬវិធីយក userId របស់អ្នក
	        @Valid @RequestBody CreateGroupConversationRequest request) {
	    ConversationResponse response = conversationService.createGroupConversation(principal.getId(), request);
	    return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/last/{id}/messages")
	public ResponseEntity<MessageResponse> getLastMessage(@PathVariable String id) {
		return ResponseEntity.ok(messageService.lastMessageByConversation(id));
	}

	/**
	 * Get messages from a conversation with cursor-based pagination
	 */
	@GetMapping("/{id}/messages")
	public ResponseEntity<Page<MessageResponse>> getMessages(@PathVariable String id,
			@AuthenticationPrincipal User user,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
			@PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return ResponseEntity.ok(messageService.getConversationMessages(id, user.getId(), before, pageable));
	}

	/**
	 * Send a message to a conversation
	 */
	@PostMapping("/{id}/messages")
	public ResponseEntity<MessageResponse> sendMessage(@PathVariable String id, @AuthenticationPrincipal User user,
			@Valid @RequestBody SendMessageRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(messageService.sendMessage(id, user.getId(), request));
	}

	/**
	 * Edit a message
	 */
	@PutMapping("/messages/{messageId}")
	public ResponseEntity<MessageResponse> editMessage(@PathVariable String messageId,
			@AuthenticationPrincipal User user, @Valid @RequestBody SendMessageRequest request) {
		return ResponseEntity.ok(messageService.editMessage(messageId, user.getId(), request));
	}

	/**
	 * Delete a message (soft delete)
	 */
	@DeleteMapping("/messages/{messageId}")
	public ResponseEntity<Void> deleteMessage(@PathVariable String messageId, @AuthenticationPrincipal User user) {
		messageService.deleteMessage(messageId, user.getId());
		return ResponseEntity.noContent().build();
	}

	/**
	 * Mark a message as read
	 */
	@PostMapping("/{conversationId}/messages/{messageId}/read")
	public ResponseEntity<Void> markMessageAsRead(@PathVariable String conversationId, @PathVariable String messageId,
			@AuthenticationPrincipal User user) {
		messageService.markMessageAsRead(messageId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Mark all messages in a conversation as read
	 */
	@PostMapping("/{conversationId}/read-all")
	public ResponseEntity<Void> markAllMessagesAsRead(@PathVariable String conversationId,
			@AuthenticationPrincipal User user) {
		messageService.markAllMessagesAsRead(conversationId);
		return ResponseEntity.noContent().build();
	}
}
