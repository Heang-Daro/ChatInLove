package org.dr.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * traffic passes through here. The server simply forwards each message to the
 * target user's private queue.
 *
 * Message types (sent in the "type" field of the payload):
 *   CALL_OFFER    – caller → callee, contains SDP offer + callType (AUDIO|VIDEO)
 *   CALL_ANSWER   – callee → caller, contains SDP answer
 *   ICE_CANDIDATE – either peer, contains ICE candidate
 *   CALL_REJECT   – callee → caller, callee declined
 *   CALL_CANCEL   – caller → callee, caller hung up before answer
 *   CALL_HANGUP   – either peer, call ended
 *   CALL_BUSY     – callee → caller, callee is already in a call
 *
 * Routing:
 *   Client sends to  : /app/signal/{targetUserId}
 *   Server forwards to: /user/{targetUserId}/queue/signal
 *
 * The payload is a free-form JSON object that always includes:
 *   {
 *     "type"           : "CALL_OFFER" | "CALL_ANSWER" | ... ,
 *     "fromUserId"     : "<sender's userId>",
 *     "conversationId" : "<conversationId>",
 *     "callType"       : "AUDIO" | "VIDEO",   // present in CALL_OFFER
 *     "sdp"            : { "type": "offer"|"answer", "sdp": "..." }, // offer/answer
 *     "candidate"      : { "candidate": "...", "sdpMid": "...", "sdpMLineIndex": 0 } // ICE
 *   }
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SignalingController {

    private final SimpMessagingTemplate messaging;

    /**
     * Relay any signaling message to the target user's private queue.
     *
     * @param targetUserId  the user ID of the recipient (path variable)
     * @param payload       the raw JSON object from the client
     * @param principal     the authenticated sender (injected by Spring)
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

        // Stamp the sender's userId so the receiver always knows who it's from.
        payload.put("fromUserId", principal.getName());

        String msgType = payload.getOrDefault("type", "UNKNOWN").toString();
        log.debug("Signaling [{}] from {} → {}", msgType, principal.getName(), targetUserId);

        messaging.convertAndSendToUser(targetUserId, "/queue/signal", payload);
    }
}
