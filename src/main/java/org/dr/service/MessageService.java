package org.dr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.dr.dto.request.CreatePrivateConversationRequest;
import org.dr.dto.request.SendMessageRequest;
import org.dr.dto.response.ConversationResponse;
import org.dr.dto.response.MessageResponse;
import org.dr.exception.ResourceNotFoundException;
import org.dr.exception.UnauthorizedException;
import org.dr.model.Conversation;
import org.dr.model.Message;
import org.dr.model.User;
import org.dr.model.enums.MessageType;
import org.dr.repository.ConversationMemberRepository;
import org.dr.repository.ConversationRepository;
import org.dr.repository.MessageRepository;
import org.dr.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate; // <-- 1. Import this
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate; // <-- 2. Inject messaging template
    private final StorageUrlService storageUrlService;

    /**
     * Send a message to a conversation
     */
    @Transactional
    public MessageResponse sendMessage(String conversationId, String senderId, SendMessageRequest request) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        // Check if user is an active member of the conversation
        var memberOpt = conversationMemberRepository.findByConversationIdAndUserId(conversationId, senderId);
        if (memberOpt.isEmpty() || memberOpt.get().getLeftAt() != null) {
            throw new UnauthorizedException("User is not an active member of this conversation");
        }

        // Create and save message
        MessageType msgType = request.messageType() != null ? request.messageType() : MessageType.TEXT;

        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .type(msgType)
                // Store an absolute URL so the client can render the file directly.
                .content(storageUrlService.toAbsoluteUrl(request.content()))
                .build();

        message = messageRepository.save(message);

        // Update conversation's lastMessageAt
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        log.info("Message {} sent to conversation {} by user {}", message.getId(), conversationId, senderId);

        MessageResponse response = toResponse(message);

        // 1. Broadcast the saved message to the room topic in real time!
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, response);

        // 2. Broadcast to all members' private user queues (/user/queue/private) for AllChat real-time updates
        if (conversation.getMembers() != null) {
            for (var member : conversation.getMembers()) {
                if (member.getLeftAt() == null && member.getUser() != null) {
                    try {
                        messagingTemplate.convertAndSendToUser(
                                member.getUser().getId(),
                                "/queue/private",
                                response
                        );
                    } catch (Exception e) {
                        log.warn("Could not send private queue message to user {}: {}", member.getUser().getId(), e.getMessage());
                    }
                }
            }
        }

        // Clear typing indicator for this conversation when a new message arrives
        try {
            messagingTemplate.convertAndSend(
                    "/topic/conversations/" + conversationId + "/typing",
                    java.util.Map.of(
                            "userId", senderId,
                            "username", sender.getUsername(),
                            "conversationId", conversationId,
                            "isTyping", false
                    )
            );
        } catch (Exception e) {
            log.warn("Could not broadcast typing stop notification: {}", e.getMessage());
        }

        return response;
    }

    @Transactional(readOnly = true)
    public MessageResponse lastMessageByConversation(String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        Message message = messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversationId).orElse(new Message());
        return toResponse(message);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationMessages(String conversationId, String userId, LocalDateTime before, Pageable pageable) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (!conversationMemberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw new UnauthorizedException("User is not a member of this conversation");
        }

        if (before != null) {
            return messageRepository.findByConversationIdBefore(conversationId, before, pageable)
                    .map(this::toResponse);
        }

        return messageRepository.findByConversationId(conversationId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public MessageResponse editMessage(String messageId, String userId, SendMessageRequest request) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        if (!message.getSender().getId().equals(userId)) {
            throw new UnauthorizedException("Only the sender can edit this message");
        }

        if (message.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Message has been deleted");
        }

        message.setContent(request.content());
        message.setEditedAt(LocalDateTime.now());
        message = messageRepository.save(message);

        return toResponse(message);
    }

    @Transactional
    public void deleteMessage(String messageId, String userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        if (!message.getSender().getId().equals(userId)) {
            throw new UnauthorizedException("Only the sender can delete this message");
        }

        message.setDeletedAt(LocalDateTime.now());
        messageRepository.save(message);

        log.info("Message {} soft-deleted by user {}", messageId, userId);
    }

    @Transactional
    public void markMessageAsRead(String messageId) {
        messageRepository.markAsRead(messageId);
    }

    @Transactional
    public void markAllMessagesAsRead(String conversationId) {
        messageRepository.markAllAsRead(conversationId);
    }

    private MessageResponse toResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .type(message.getType())
                .content(message.getContent())
                .isRead(message.isRead())
                .createdAt(message.getCreatedAt())
                .editedAt(message.getEditedAt())
                .deletedAt(message.getDeletedAt())
                .relatedCallId(message.getRelatedCallId())
                .build();
    }

    @Transactional
    public MessageResponse sendPrivateMessage(String senderId, CreatePrivateConversationRequest recipient, SendMessageRequest request) {
        if (senderId.equals(recipient.recipientId())) {
            throw new IllegalArgumentException("Cannot message yourself");
        }
        ConversationResponse conversation = conversationService.getOrCreatePrivateConversation(senderId, recipient);
        return sendMessage(conversation.id(), senderId, request);
    }
}