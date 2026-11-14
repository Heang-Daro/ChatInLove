package org.dr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.dto.request.CreateGroupConversationRequest;
import org.dr.dto.request.CreatePrivateConversationRequest;
import org.dr.dto.response.ConversationMemberResponse;
import org.dr.dto.response.ConversationResponse;
import org.dr.dto.response.MessageResponse;
import org.dr.exception.ResourceNotFoundException;
import org.dr.exception.UnauthorizedException;
import org.dr.model.Conversation;
import org.dr.model.ConversationMember;
import org.dr.model.Message;
import org.dr.model.User;
import org.dr.model.enums.ConversationStatus;
import org.dr.model.enums.ConversationMemberRole;
import org.dr.model.enums.ConversationType;
import org.dr.repository.ConversationMemberRepository;
import org.dr.repository.ConversationRepository;
import org.dr.repository.MessageRepository;
import org.dr.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getUserConversations(String userId, Pageable pageable) {
        return conversationRepository.findActiveConversationsForUser(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getPendingRequests(String userId, Pageable pageable) {
        return conversationRepository.findPendingRequestsForRecipient(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        if (!conversationMemberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw new UnauthorizedException("User is not a member of this conversation");
        }
        if (conversation.getStatus() == ConversationStatus.PENDING
                && !userId.equals(conversation.getRequesterId())) {
            throw new UnauthorizedException("Accept the chat request before opening this conversation");
        }
        if (conversation.getStatus() == ConversationStatus.REJECTED) {
            throw new UnauthorizedException("This chat request was rejected");
        }
        return toResponse(conversation);
    }

    @Transactional
    public ConversationResponse createGroupConversation(String userId, CreateGroupConversationRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));
        Conversation conversation = Conversation.builder()
                .type(ConversationType.GROUP)
                .name(request.name())
                .description(request.description())
                .status(ConversationStatus.ACCEPTED)
                .build();
        conversation = conversationRepository.save(conversation);
        addMember(conversation, creator, ConversationMemberRole.ADMIN);

        if (request.memberIds() != null) {
            for (String memberId : request.memberIds()) {
                if (!memberId.equals(userId)) {
                    User memberUser = userRepository.findById(memberId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + memberId));
                    addMember(conversation, memberUser, ConversationMemberRole.MEMBER);
                }
            }
        }
        return toResponse(conversation);
    }

    @Transactional
    public ConversationResponse getOrCreatePrivateConversation(String userId, CreatePrivateConversationRequest request) {
        if (userId.equals(request.recipientId())) throw new IllegalArgumentException("Cannot message yourself");
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User recipientUser = userRepository.findById(request.recipientId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));

        String minId = currentUser.getId().compareTo(recipientUser.getId()) < 0 ? currentUser.getId() : recipientUser.getId();
        String maxId = currentUser.getId().compareTo(recipientUser.getId()) < 0 ? recipientUser.getId() : currentUser.getId();
        String privateKey = minId + "_" + maxId;

        var existing = conversationRepository.findByPrivateKey(privateKey);
        if (existing.isPresent()) {
            Conversation conversation = existing.get();
            if (conversation.getStatus() == ConversationStatus.REJECTED) {
                conversation.setStatus(ConversationStatus.PENDING);
                conversation.setRequesterId(userId);
                return toResponse(conversationRepository.save(conversation));
            }
            return toResponse(conversation);
        }

        Conversation conversation = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .privateKey(privateKey)
                .requesterId(userId)
                .status(ConversationStatus.PENDING)
                .build();
        conversation = conversationRepository.save(conversation);
        addMember(conversation, currentUser, ConversationMemberRole.MEMBER);
        addMember(conversation, recipientUser, ConversationMemberRole.MEMBER);
        return toResponse(conversation);
    }

    @Transactional
    public ConversationResponse respondToRequest(String conversationId, String userId, boolean accept) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat request not found"));
        if (conversation.getType() != ConversationType.PRIVATE
                || !conversationMemberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw new UnauthorizedException("You are not the recipient of this chat request");
        }
        if (userId.equals(conversation.getRequesterId())) {
            throw new UnauthorizedException("The sender cannot respond to their own chat request");
        }
        if (conversation.getStatus() != ConversationStatus.PENDING) {
            throw new IllegalArgumentException("This chat request has already been handled");
        }
        conversation.setStatus(accept ? ConversationStatus.ACCEPTED : ConversationStatus.REJECTED);
        return toResponse(conversationRepository.save(conversation));
    }

    @Transactional
    private void addMember(Conversation conversation, User user, ConversationMemberRole role) {
        ConversationMember member = ConversationMember.builder()
                .conversation(conversation)
                .user(user)
                .role(role)
                .joinedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        conversationMemberRepository.save(member);
    }

    @Transactional
    public void markMessageAsRead(String conversationId, String messageId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        if (conversation.getStatus() != ConversationStatus.ACCEPTED
                && conversation.getStatus() != null) {
            throw new UnauthorizedException("Accept the chat request before reading messages");
        }
        ConversationMember member = conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new UnauthorizedException("User is not a member of this conversation"));
        member.setLastReadMessageId(messageId);
        conversationMemberRepository.save(member);
    }

    private ConversationResponse toResponse(Conversation conversation) {
        var members = conversationMemberRepository.findByConversationIdAndLeftAtIsNull(conversation.getId())
                .stream()
                .map(m -> ConversationMemberResponse.builder()
                        .userId(m.getUser().getId())
                        .username(m.getUser().getUsername())
                        .role(m.getRole())
                        .joinedAt(m.getJoinedAt())
                        .leftAt(m.getLeftAt())
                        .profileImageUrl(m.getUser().getProfileImageUrl())
                        .build())
                .collect(Collectors.toList());

        MessageResponse lastMessage = messageRepository
                .findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .map(this::toMessageResponse)
                .orElse(null);

        return ConversationResponse.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .name(conversation.getName())
                .description(conversation.getDescription())
                .createdAt(conversation.getCreatedAt())
                .lastMessageAt(conversation.getLastMessageAt())
                .members(members)
                .lastMessage(lastMessage)
                .status(conversation.getStatus() == null ? ConversationStatus.ACCEPTED : conversation.getStatus())
                .requesterId(conversation.getRequesterId())
                .build();
    }

    private MessageResponse toMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .conversationId(message.getConversation().getId())
                .type(message.getType())
                .isRead(message.isRead())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
