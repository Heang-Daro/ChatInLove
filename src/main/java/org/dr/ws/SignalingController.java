package org.dr.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.repository.ConversationMemberRepository;
import org.dr.repository.ConversationRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * WebRTC signaling relay via STOMP.
 *
 * All signaling messages are routed through the server as a relay — no media
 * traffic passes through here.
 *
 * Message types (sent in the "type" field):
 *   CALL_OFFER         – caller → callee, contains SDP offer + callType (AUDIO|VIDEO)
 *   CALL_ANSWER        – callee → caller, contains SDP answer
 *   ICE_CANDIDATE      – either peer, contains ICE candidate
 *   CALL_REJECT        – callee → caller, callee declined
 *   CALL_CANCEL        – caller → callee, caller hung up before answer
 *   CALL_HANGUP        – either peer, call ended
 *   CALL_BUSY          – callee → caller, callee is already in a call
 *   GROUP_CALL_STARTED – caller → all group members (broadcast on conversation topic)
 *   JOIN_CALL          – member → caller, member wants to join the group call
 *   LEAVE_CALL         – either, leaving the group call
 *
 * Routing:
 *   1-to-1 / targeted : Client sends to /app/signal/{targetUserId}
 *                        Server forwards to /user/{targetUserId}/queue/signal
 *   Group broadcast    : Client sends to /app/signal/group/{conversationId}
 *                        Server broadcasts to /topic/conversations/{conversationId}/call
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SignalingController {

    private final SimpMessagingTemplate messaging;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;

    /**
     * Relay a 1-to-1 or targeted signaling message to a specific user.
     */
    @MessageMapping("/signal/{targetUserId}")
    public void relay(
            @DestinationVariable String targetUserId,
            @Payload Map<String, Object> payload,
            Principal principal) {

        if (principal == null) {
            log.warn("Unauthenticated signaling message dropped");
            return;
        }

        payload.put("fromUserId", principal.getName());

        String msgType = payload.getOrDefault("type", "UNKNOWN").toString();
        log.debug("Signaling [{}] from {} → {}", msgType, principal.getName(), targetUserId);

        messaging.convertAndSendToUser(targetUserId, "/queue/signal", payload);
    }

    /**
     * Broadcast a group call event (GROUP_CALL_STARTED / LEAVE_CALL) to all
     * members of a conversation by publishing on the conversation's call topic.
     *
     * Client sends to: /app/signal/group/{conversationId}
     * Delivered on  : /topic/conversations/{conversationId}/call
     */
    @MessageMapping("/signal/group/{conversationId}")
    public void broadcastGroupSignal(
            @DestinationVariable String conversationId,
            @Payload Map<String, Object> payload,
            Principal principal) {

        if (principal == null) {
            log.warn("Unauthenticated group signaling message dropped");
            return;
        }

        payload.put("fromUserId", principal.getName());
        payload.put("conversationId", conversationId);

        String msgType = payload.getOrDefault("type", "UNKNOWN").toString();
        log.debug("Group signaling [{}] from {} on conv {}", msgType, principal.getName(), conversationId);

        // Broadcast to the conversation's call topic.
        messaging.convertAndSend(
                "/topic/conversations/" + conversationId + "/call", payload);

        // Also send directly to each member's private queue so members who
        // are not subscribed to the topic (e.g. not in that chat screen) still
        // receive the event.
        var conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation != null && conversation.getMembers() != null) {
            for (var member : conversation.getMembers()) {
                if (member.getLeftAt() == null && member.getUser() != null) {
                    String memberId = member.getUser().getId();
                    if (!memberId.equals(principal.getName())) {
                        messaging.convertAndSendToUser(memberId, "/queue/signal", payload);
                    }
                }
            }
        }
    }
}
