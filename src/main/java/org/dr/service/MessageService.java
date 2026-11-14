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
import org.dr.model.enums.ConversationStatus;
import org.dr.model.enums.ConversationType;
import org.dr.model.enums.MessageType;
import org.dr.repository.ConversationMemberRepository;
import org.dr.repository.ConversationRepository;
import org.dr.repository.MessageRepository;
import org.dr.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final StorageUrlService storageUrlService;

    @Transactional
    public MessageResponse sendMessage(String conversationId, String senderId, SendMessageRequest request) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        var memberOpt = conversationMemberRepository.findByConversationIdAndUserId(conversationId, senderId);
        if (memberOpt.isEmpty() || memberOpt.get().getLeftAt() != null) {
            throw new UnauthorizedException("User is not an active member of this conversation");
        }

        if (conversation.getStatus() == ConversationStatus.REJECTED) {
            throw new UnauthorizedException("This chat request was rejected");
        }
        if (conversation.getStatus() == ConversationStatus.PENDING) {
            if (!senderId.equals(conversation.getRequesterId())) {
                throw new UnauthorizedException("Accept the chat request before sending a message");
            }
            if (messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversationId).isPresent()) {
                throw new UnauthorizedException("Only one message can be sent with a chat request");
            }
        }

        MessageType msgType = request.messageType() != null ? request.messageType() : MessageType.TEXT;
        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .type(msgType)
                .content(storageUrlService.toAbsoluteUrl(request.content()))
                .build();
        message = messageRepository.save(message);
        conversation.setLastMessageAt(LocalDateTime.now(ZoneOffset.UTC));
        conversationRepository.save(conversation);
        MessageResponse response = toResponse(message);

        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, response);
        if (conversation.getMembers() != null) {
            for (var member : conversation.getMembers()) {
                if (member.getLeftAt() == null && member.getUser() != null) {
                    try {
                        messagingTemplate.convertAndSendToUser(member.getUser().getId(), "/queue/private", response);
                    } catch (Exception e) {
                        log.warn("Could not notify user {}: {}", member.getUser().getId(), e.getMessage());
                    }
                }
            }
        }
        if (conversation.getStatus() == ConversationStatus.PENDING) {
            String recipientId = conversation.getMembers().stream()
                    .map(m -> m.getUser().getId())
                    .filter(id -> !id.equals(conversation.getRequesterId()))
                    .findFirst().orElse(null);
            if (recipientId != null) {
                messagingTemplate.convertAndSendToUser(recipientId, "/queue/notifications",
                        java.util.Map.of("type", "CHAT_REQUEST", "conversationId", conversationId));
            }
        }
        try {
            messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/typing",
                    java.util.Map.of("userId", senderId, "username", sender.getUsername(),
                            "conversationId", conversationId, "isTyping", false));
        } catch (Exception e) {
            log.warn("Could not broadcast typing stop notification: {}", e.getMessage());
        }
        return response;
    }

    @Transactional(readOnly = true)
    public MessageResponse lastMessageByConversation(String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        return messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversationId)
                .map(this::toResponse).orElse(new MessageResponse(null, conversationId, null, null, null,
                        null, false, null, null, null, null));
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationMessages(String conversationId, String userId,
            LocalDateTime before, Pageable pageable) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        if (!conversationMemberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw new UnauthorizedException("User is not a member of this conversation");
        }
        if (conversation.getStatus() == ConversationStatus.REJECTED
                || (conversation.getStatus() == ConversationStatus.PENDING
                    && !userId.equals(conversation.getRequesterId()))) {
            throw new UnauthorizedException("You cannot access this chat before accepting its request");
        }
        if (before != null) return messageRepository.findByConversationIdBefore(conversationId, before, pageable).map(this::toResponse);
        return messageRepository.findByConversationId(conversationId, pageable).map(this::toResponse);
    }

    @Transactional
    public MessageResponse editMessage(String messageId, String userId, SendMessageRequest request) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!message.getSender().getId().equals(userId)) throw new UnauthorizedException("Only the sender can edit this message");
        if (message.getDeletedAt() != null) throw new ResourceNotFoundException("Message has been deleted");
        message.setContent(request.content());
        message.setEditedAt(LocalDateTime.now(ZoneOffset.UTC));
        return toResponse(messageRepository.save(message));
    }

    @Transactional
    public void deleteMessage(String messageId, String userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!message.getSender().getId().equals(userId)) throw new UnauthorizedException("Only the sender can delete this message");
        message.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        messageRepository.save(message);
    }

    @Transactional
    public void markMessageAsRead(String messageId) { messageRepository.markAsRead(messageId); }

    @Transactional
    public void markAllMessagesAsRead(String conversationId) { messageRepository.markAllAsRead(conversationId); }

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
    public MessageResponse sendPrivateMessage(String senderId, CreatePrivateConversationRequest recipient,
            SendMessageRequest request) {
        if (senderId.equals(recipient.recipientId())) throw new IllegalArgumentException("Cannot message yourself");
        ConversationResponse conversation = conversationService.getOrCreatePrivateConversation(senderId, recipient);
        return sendMessage(conversation.id(), senderId, request);
    }
}
