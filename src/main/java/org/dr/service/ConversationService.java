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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
//    private final MessageRepository messageRepository;

    /**
     * Get all active conversations for the current user with pagination
     */
    @Transactional(readOnly = true)
    public Page<ConversationResponse> getUserConversations(String userId, Pageable pageable) {
        return conversationRepository.findActiveConversationsForUser(userId, pageable)
                .map(conversation -> {
                    Message last = messageRepository
                            .findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId())
                            .orElse(null);   // chat with no messages yet
                    return toResponse(conversation);
                });
    }
    

    /**
     * Get a specific conversation by ID with membership validation
     */
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        // Check membership
        if (!conversationMemberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw new UnauthorizedException("User is not a member of this conversation");
        }

        return toResponse(conversation);
    }
    /**
     * Create a new group conversation
     */
    @Transactional
    public ConversationResponse createGroupConversation(String userId, CreateGroupConversationRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));

        // ១. បង្កើត Conversation ប្រភេទ GROUP
        Conversation conversation = Conversation.builder()
                .type(ConversationType.GROUP)
                .name(request.name())
                .description(request.description())
                .build();
        
        conversation = conversationRepository.save(conversation);

        // ២. បន្ថែមអ្នកបង្កើតជា ADMIN របស់គ្រុប
        addMember(conversation, creator, ConversationMemberRole.ADMIN);

        // ៣. បន្ថែមសមាជិកอื่นๆ ចូលក្នុងគ្រុប (ជា MEMBER)
        if (request.memberIds() != null) {
            for (String memberId : request.memberIds()) {
                // កុំឱ្យបន្ថែមអ្នកបង្កើតម្តងទៀតបើមានក្នុងបញ្ជី
                if (!memberId.equals(userId)) {
                    User memberUser = userRepository.findById(memberId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + memberId));
                    addMember(conversation, memberUser, ConversationMemberRole.MEMBER);
                }
            }
        }

        return toResponse(conversation);
    }
    

    /**
     * Create or get a private conversation between two users
     */
    
    @Transactional
    public ConversationResponse getOrCreatePrivateConversation(String userId, CreatePrivateConversationRequest request) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User recipientUser = userRepository.findById(request.recipientId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));

        // Generate private key (minUserId_maxUserId)
        String minId = currentUser.getId().compareTo(recipientUser.getId()) < 0 ?
                currentUser.getId() : recipientUser.getId();
        String maxId = currentUser.getId().compareTo(recipientUser.getId()) < 0 ?
                recipientUser.getId() : currentUser.getId();
        String privateKey = minId + "_" + maxId;

        // Check if conversation already exists
        return conversationRepository.findByPrivateKey(privateKey)
                .map(this::toResponse)
                .orElseGet(() -> {
                    // Create new private conversation
                    Conversation conversation = Conversation.builder()
                            .type(ConversationType.PRIVATE)
                            .privateKey(privateKey)
                            .build();
                    conversation = conversationRepository.save(conversation);

                    // Add both users as members
                    addMember(conversation, currentUser, ConversationMemberRole.MEMBER);
                    addMember(conversation, recipientUser, ConversationMemberRole.MEMBER);

                    return toResponse(conversation);
                });
    }

    /**
     * Add a member to a conversation
     */
    @Transactional
    private void addMember(Conversation conversation, User user, ConversationMemberRole role) {
        ConversationMember member = ConversationMember.builder()
                .conversation(conversation)
                .user(user)
                .role(role)
                .joinedAt(LocalDateTime.now())
                .build();
        conversationMemberRepository.save(member);
    }

    /**
     * Mark a message as read by the current user
     */
    @Transactional
    public void markMessageAsRead(String conversationId, String messageId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

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
                        .build())
                .collect(Collectors.toList());

        MessageResponse lastMessage = messageRepository
                .findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .map(this::toMessageResponse)
                .orElse(null);   // chat with no messages yet

        return ConversationResponse.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .name(conversation.getName())
                .description(conversation.getDescription())
                .createdAt(conversation.getCreatedAt())
                .lastMessageAt(conversation.getLastMessageAt())
                .members(members)
                .lastMessage(lastMessage)   // NEW
                .build();
    }

    private MessageResponse toMessageResponse(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .content(m.getContent())
                .senderId(m.getSender().getId())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
