package org.dr.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.dr.dto.request.CreatePrivateConversationRequest;
import org.dr.dto.request.SendMessageRequest;
import org.dr.dto.response.MessageResponse;
import org.dr.model.Conversation;
import org.dr.model.ConversationMember;
import org.dr.model.User;
import org.dr.repository.ConversationRepository;
import org.dr.repository.UserRepository;
import org.dr.service.MessageService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import jakarta.validation.Valid;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
@CrossOrigin("*")
@Slf4j
public class ChatSocketController {
    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;

    /**
     * Handle sending a message to a conversation
     * Client sends to: /app/chat.send/{conversationId}
     * Broadcasts to: /topic/conversations/{conversationId}
     */
    @MessageMapping("/chat.send/{conversationId}")
    @SendTo("/topic/conversations/{conversationId}")
    public MessageResponse sendMessage(
            @DestinationVariable String conversationId,
            @Payload SendMessageRequest request,
            Principal principal) {
        
        String userId = extractUserId(principal);
        log.debug("Message received from user {} for conversation {}", userId, conversationId);
        
        MessageResponse res = messageService.sendMessage(conversationId, userId, request);
        
        // Notify all conversation members on their private queue & global inbox queue
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation != null && conversation.getMembers() != null) {
            for (ConversationMember member : conversation.getMembers()) {
                if (member.getLeftAt() == null && member.getUser() != null) {
                    String memberId = member.getUser().getId();
                    
                    // 1. Private chat item update
                    messagingTemplate.convertAndSendToUser(
                            memberId, 
                            "/queue/private", 
                            res
                    );
                    
                    // 2. Global inbox update (fixes missing /user/queue/conversations sync)
                    messagingTemplate.convertAndSendToUser(
                            memberId, 
                            "/queue/conversations", 
                            res
                    );
                }
            }
        }
        
        return res;
    }

    @MessageMapping("/chat.group/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public MessageResponse sendGroup(@DestinationVariable String roomId,
                                     @Valid @Payload SendMessageRequest request,
                                     Principal principal) {
        MessageResponse res = messageService.sendMessage(roomId, extractUserId(principal), request);
        
        Conversation conversation = conversationRepository.findById(roomId).orElse(null);
        if (conversation != null && conversation.getMembers() != null) {
            for (ConversationMember member : conversation.getMembers()) {
                if (member.getLeftAt() == null && member.getUser() != null) {
                    String memberId = member.getUser().getId();
                    
                    messagingTemplate.convertAndSendToUser(
                            memberId, 
                            "/queue/private", 
                            res
                    );
                    
                    messagingTemplate.convertAndSendToUser(
                            memberId, 
                            "/queue/conversations", 
                            res
                    );
                }
            }
        }

        return res;
    }

    @MessageMapping("/chat.private/{recipientId}")
    public void sendPrivate(@DestinationVariable String recipientId,
                            @Valid @Payload SendMessageRequest request,
                            Principal principal) {
        String senderId = extractUserId(principal);
        
        CreatePrivateConversationRequest recipientReq = new CreatePrivateConversationRequest(recipientId);
        
        MessageResponse res = messageService.sendPrivateMessage(senderId, recipientReq, request);
        
        messagingTemplate.convertAndSendToUser(recipientId, "/queue/private", res);
        messagingTemplate.convertAndSendToUser(recipientId, "/queue/conversations", res);
        
        messagingTemplate.convertAndSendToUser(senderId, "/queue/private", res);
        messagingTemplate.convertAndSendToUser(senderId, "/queue/conversations", res);
    }

    /**
     * Handle typing notification
     */
    @MessageMapping("/chat.typing/{conversationId}")
    @SendTo("/topic/conversations/{conversationId}/typing")
    public TypingNotification handleTyping(
            @DestinationVariable String conversationId,
            @Payload(required = false) java.util.Map<String, Object> payload,
            Principal principal) {
        
        String userId = extractUserId(principal);
        User user = userRepository.findById(userId).orElse(null);
        
        boolean isTyping = true;
        if (payload != null && payload.containsKey("isTyping")) {
            Object val = payload.get("isTyping");
            if (val instanceof Boolean) {
                isTyping = (Boolean) val;
            }
        }
        
        if (user != null) {
            return TypingNotification.builder()
                    .userId(userId)
                    .username(user.getUsername())
                    .conversationId(conversationId)
                    .isTyping(isTyping)
                    .build();
        }
        
        return null;
    }

    private String extractUserId(Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("User is not authenticated");
        }
        return principal.getName();
    }

    static class TypingNotification {
        String userId;
        String username;
        String conversationId;
        boolean isTyping;

        TypingNotification(String userId, String username, String conversationId, boolean isTyping) {
            this.userId = userId;
            this.username = username;
            this.conversationId = conversationId;
            this.isTyping = isTyping;
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder {
            String userId;
            String username;
            String conversationId;
            boolean isTyping;

            Builder userId(String userId) { this.userId = userId; return this; }
            Builder username(String username) { this.username = username; return this; }
            Builder conversationId(String conversationId) { this.conversationId = conversationId; return this; }
            Builder isTyping(boolean isTyping) { this.isTyping = isTyping; return this; }

            TypingNotification build() {
                return new TypingNotification(userId, username, conversationId, isTyping);
            }
        }

        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getConversationId() { return conversationId; }
        public boolean isTyping() { return isTyping; }
    }
}