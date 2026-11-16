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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    /** Maximum length of the message preview that is returned with a conversation. */
    private static final int LAST_MESSAGE_PREVIEW_LENGTH = 120;

    @Transactional(readOnly = true)
    public Slice<ConversationResponse> getUserConversations(String userId, Pageable pageable,
                                                             LocalDateTime since) {
        Slice<Conversation> conversations = (since != null)
                ? conversationRepository.findActiveConversationsForUserChangedSince(userId, since, pageable)
                : conversationRepository.findActiveConversationsForUser(userId, pageable);
        return toResponseSlice(conversations, true);
    }

    @Transactional(readOnly = true)
    public Slice<ConversationResponse> getPendingRequests(String userId, Pageable pageable) {
        Slice<Conversation> conversations =
                conversationRepository.findPendingRequestsForRecipient(userId, pageable);
        return toResponseSlice(conversations, true);
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
        // A single conversation: the preview is not trimmed, the client may
        // want the full text of the last message.
        return toResponses(List.of(conversation), false).get(0);
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
        return toResponses(List.of(conversation), false).get(0);
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
                return toResponses(List.of(conversationRepository.save(conversation)), false).get(0);
            }
            return toResponses(List.of(conversation), false).get(0);
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
        return toResponses(List.of(conversation), false).get(0);
    }

    @Transactional
    public ConversationResponse respondToRequest(String conversationId, String userId, boolean accept) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat request not found"));
        if (conversation.getType() != ConversationType.PRIVATE
                || !conversationMemberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw new UnauthorizedException("You are the recipient of this chat request");
        }
        if (userId.equals(conversation.getRequesterId())) {
            throw new UnauthorizedException("The sender cannot respond to their own chat request");
        }
        if (conversation.getStatus() != ConversationStatus.PENDING) {
            throw new IllegalArgumentException("This chat request has already been handled");
        }
        conversation.setStatus(accept ? ConversationStatus.ACCEPTED : ConversationStatus.REJECTED);
        return toResponses(List.of(conversationRepository.save(conversation)), false).get(0);
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

    // ---------------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------------

    /** Keeps the {@code hasNext} flag of the source slice. */
    private Slice<ConversationResponse> toResponseSlice(Slice<Conversation> conversations,
                                                        boolean trimPreview) {
        List<ConversationResponse> responses = toResponses(conversations.getContent(), trimPreview);
        return new SliceImpl<>(responses, conversations.getPageable(), conversations.hasNext());
    }

    /**
     * Maps a whole page of conversations with a constant number of queries.
     *
     * <p>Before this was one query per conversation for the members, one
     * query per member for the user and one query per conversation for the
     * last message - roughly 140 statements for a page of 20 conversations.
     * Now it is 3 statements in total (conversations, members, last messages).
     *
     * @param trimPreview shorten the content of the last message (the chat list
     *                    only needs a preview)
     */
    private List<ConversationResponse> toResponses(List<Conversation> conversations, boolean trimPreview) {
        if (conversations == null || conversations.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> conversationIds = conversations.stream()
                .map(Conversation::getId)
                .collect(Collectors.toList());

        // 1 query for every member of every conversation (user included).
        Map<String, List<ConversationMemberResponse>> membersByConversation = new LinkedHashMap<>();
        for (ConversationMember member :
                conversationMemberRepository.findActiveMembersWithUser(conversationIds)) {
            membersByConversation
                    .computeIfAbsent(member.getConversation().getId(), key -> new ArrayList<>())
                    .add(toMemberResponse(member));
        }

        // 1 query for the last message of every conversation.
        Map<String, MessageResponse> lastMessageByConversation = new LinkedHashMap<>();
        for (Message message : messageRepository.findLastMessagesOfConversations(conversationIds)) {
            lastMessageByConversation.putIfAbsent(
                    message.getConversation().getId(),
                    toMessageResponse(message, trimPreview));
        }

        return conversations.stream()
                .map(conversation -> ConversationResponse.builder()
                        .id(conversation.getId())
                        .type(conversation.getType())
                        .name(conversation.getName())
                        .description(conversation.getDescription())
                        .createdAt(conversation.getCreatedAt())
                        .lastMessageAt(conversation.getLastMessageAt())
                        .members(membersByConversation.getOrDefault(conversation.getId(), Collections.emptyList()))
                        .lastMessage(lastMessageByConversation.get(conversation.getId()))
                        .status(conversation.getStatus() == null
                                ? ConversationStatus.ACCEPTED
                                : conversation.getStatus())
                        .requesterId(conversation.getRequesterId())
                        .build())
                .collect(Collectors.toList());
    }

    private ConversationMemberResponse toMemberResponse(ConversationMember member) {
        return ConversationMemberResponse.builder()
                .userId(member.getUser().getId())
                .username(member.getUser().getUsername())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .leftAt(member.getLeftAt())
                .profileImageUrl(member.getUser().getProfileImageUrl())
                .build();
    }

    private MessageResponse toMessageResponse(Message message, boolean trimContent) {
        String content = message.getContent();
        if (trimContent && content != null && content.length() > LAST_MESSAGE_PREVIEW_LENGTH) {
            content = content.substring(0, LAST_MESSAGE_PREVIEW_LENGTH - 3) + "...";
        }
        return MessageResponse.builder()
                .id(message.getId())
                .content(content)
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .conversationId(message.getConversation().getId())
                .type(message.getType())
                .isRead(message.isRead())
                .createdAt(message.getCreatedAt())
                .sequenceId(message.getSequenceId())
                .build();
    }
}
