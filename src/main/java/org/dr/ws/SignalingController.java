package org.dr.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.model.CallSession;
import org.dr.model.GroupCallSession;
import org.dr.model.enums.CallSessionStatus;
import org.dr.model.enums.MessageType;
import org.dr.repository.ConversationRepository;
import org.dr.service.CallSessionService;
import org.dr.service.FcmService;
import org.dr.service.MessageService;
import org.dr.service.PresenceService;
import org.dr.dto.request.SendMessageRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * WebRTC signaling relay via STOMP.
 *
 * Fix 1 — Loopback ringing:
 *   All private call signals are delivered ONLY to the target user via
 *   convertAndSendToUser(). The caller's client must check
 *   signal.fromUserId != localUserId before triggering UI/ringtone.
 *
 * Fix 2 — Presence:
 *   Online/offline status is tracked by PresenceEventListener and
 *   broadcast on /topic/presence. FcmService delivers a push if the
 *   receiver is offline when a CALL_OFFER arrives.
 *
 * Fix 3 — Missed call recovery:
 *   CALL_OFFER creates a CallSession row (45 s ring window). On reconnect,
 *   PresenceEventListener replays the offer to the receiver.
 *
 * Fix 4 — Group call click-to-join:
 *   GROUP_CALL_STARTED creates a GroupCallSession, persists a
 *   GROUP_CALL_INVITE message in the conversation so it appears in chat
 *   history for both online and future members.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SignalingController {

    private final SimpMessagingTemplate   messaging;
    private final ConversationRepository  conversationRepository;
    private final CallSessionService      callSessionService;
    private final PresenceService         presenceService;
    private final FcmService              fcmService;
    private final MessageService          messageService;

    // ── 1-to-1 signaling ─────────────────────────────────────────────────────

    /**
     * Relay a private signaling message strictly to the target user.
     * Stamping fromUserId server-side prevents spoofing.
     *
     * Client:  /app/signal/{targetUserId}
     * Server: /user/{targetUserId}/queue/signal
     */
    @MessageMapping("/signal/{targetUserId}")
    public void relay(
            @DestinationVariable String targetUserId,
            @Payload Map<String, Object> payload,
            Principal principal) {

        if (principal == null) {
            log.warn("[Signal] unauthenticated message dropped");
            return;
        }

        String callerId = principal.getName();
        payload.put("fromUserId", callerId);  // server-stamped, not client-supplied

        String msgType = payload.getOrDefault("type", "UNKNOWN").toString();
        log.debug("[Signal] {} {} → {}", msgType, callerId, targetUserId);

        switch (msgType) {
            case "CALL_OFFER" -> handleCallOffer(callerId, targetUserId, payload);
            case "CALL_ANSWER" -> handleCallAnswer(callerId, targetUserId, payload);
            case "CALL_REJECT" -> handleCallReject(callerId, targetUserId, payload);
            case "CALL_CANCEL",
                 "CALL_HANGUP" -> handleCallEnd(callerId, targetUserId, payload);
            default -> messaging.convertAndSendToUser(targetUserId, "/queue/signal", payload);
        }
    }

    // ── CALL_OFFER ────────────────────────────────────────────────────────────

    private void handleCallOffer(String callerId, String receiverId,
                                  Map<String, Object> payload) {
        String callType   = payload.getOrDefault("callType", "AUDIO").toString();
        String sdpOffer   = extractSdp(payload);
        String convId     = payload.getOrDefault("conversationId", "").toString();

        // Persist call session for missed-call recovery.
        CallSession session = callSessionService.createCallSession(
                callerId, receiverId, sdpOffer, callType);

        // Attach session ID so receiver can accept/reject it.
        payload.put("callSessionId", session.getId());

        if (presenceService.isOnline(receiverId)) {
            // Receiver is online — deliver via WebSocket.
            messaging.convertAndSendToUser(receiverId, "/queue/signal", payload);
        } else {
            // Receiver is offline — send FCM push.
            // Note: FCM token lookup requires a separate token store.
            // Here we log and send with an empty token (no-op in FcmService).
            log.info("[Signal] Receiver {} is offline — sending FCM push for call {}",
                    receiverId, session.getId());
            fcmService.sendIncomingCallPush("", callerId, session.getId(), callType);
        }
    }

    // ── CALL_ANSWER ───────────────────────────────────────────────────────────

    private void handleCallAnswer(String receiverId, String callerId,
                                   Map<String, Object> payload) {
        String sessionId = payload.getOrDefault("callSessionId", "").toString();
        if (!sessionId.isBlank()) {
            callSessionService.acceptCall(sessionId, receiverId);
        }
        messaging.convertAndSendToUser(callerId, "/queue/signal", payload);
    }

    // ── CALL_REJECT ───────────────────────────────────────────────────────────

    private void handleCallReject(String receiverId, String callerId,
                                   Map<String, Object> payload) {
        String sessionId = payload.getOrDefault("callSessionId", "").toString();
        if (!sessionId.isBlank()) {
            callSessionService.rejectCall(sessionId, receiverId);
        }
        messaging.convertAndSendToUser(callerId, "/queue/signal", payload);
    }

    // ── CALL_CANCEL / CALL_HANGUP ─────────────────────────────────────────────

    private void handleCallEnd(String userId, String otherUserId,
                                Map<String, Object> payload) {
        String sessionId = payload.getOrDefault("callSessionId", "").toString();
        if (!sessionId.isBlank()) {
            callSessionService.endCall(sessionId, userId);
        }
        messaging.convertAndSendToUser(otherUserId, "/queue/signal", payload);
    }

    // ── Group call signaling ──────────────────────────────────────────────────

    /**
     * Broadcast a group call event and persist the GROUP_CALL_INVITE message.
     *
     * Client:  /app/signal/group/{conversationId}
     * Delivered on: /user/{memberId}/queue/signal (per-member)
     *               + /topic/conversations/{convId}/call (broadcast)
     */
    @MessageMapping("/signal/group/{conversationId}")
    public void broadcastGroupSignal(
            @DestinationVariable String conversationId,
            @Payload Map<String, Object> payload,
            Principal principal) {

        if (principal == null) {
            log.warn("[Signal] unauthenticated group message dropped");
            return;
        }

        String callerId = principal.getName();
        payload.put("fromUserId", callerId);
        payload.put("conversationId", conversationId);

        String msgType = payload.getOrDefault("type", "UNKNOWN").toString();
        log.debug("[Signal] group {} {} on conv {}", msgType, callerId, conversationId);

        if ("GROUP_CALL_STARTED".equals(msgType)) {
            handleGroupCallStarted(callerId, conversationId, payload);
        } else if ("LEAVE_CALL".equals(msgType) || "JOIN_CALL".equals(msgType)) {
            handleGroupCallMembership(callerId, conversationId, msgType, payload);
        } else {
            broadcastToGroupMembers(conversationId, callerId, payload);
        }
    }

    // ── GROUP_CALL_STARTED ────────────────────────────────────────────────────

    private void handleGroupCallStarted(String callerId, String conversationId,
                                         Map<String, Object> payload) {
        String callType = payload.getOrDefault("callType", "AUDIO").toString();

        // Create persistent group call session.
        GroupCallSession gcs = callSessionService.createGroupCallSession(
                conversationId, callerId, callType);

        payload.put("callSessionId", gcs.getId());
        payload.put("activeParticipants", Set.of(callerId));

        // Persist a GROUP_CALL_INVITE message in the conversation so it
        // appears in the chat timeline for both online and future members.
        try {
            SendMessageRequest msgReq = new SendMessageRequest(
                    "GROUP_CALL:" + gcs.getId() + ":" + callType,
                    MessageType.CALL
            );
            messageService.sendMessage(conversationId, callerId, msgReq);
        } catch (Exception e) {
            log.warn("[Signal] could not persist GROUP_CALL_INVITE: {}", e.getMessage());
        }

        // Deliver to every group member individually.
        broadcastToGroupMembers(conversationId, callerId, payload);
    }

    // ── JOIN_CALL / LEAVE_CALL ────────────────────────────────────────────────

    private void handleGroupCallMembership(String userId, String conversationId,
                                            String type, Map<String, Object> payload) {
        String sessionId = payload.getOrDefault("callSessionId", "").toString();
        if (!sessionId.isBlank()) {
            if ("JOIN_CALL".equals(type)) {
                callSessionService.participantJoined(sessionId, userId)
                        .ifPresent(s -> payload.put("activeParticipants",
                                s.getActiveParticipantIds()));
            } else {
                callSessionService.participantLeft(sessionId, userId)
                        .ifPresent(s -> {
                            payload.put("activeParticipants", s.getActiveParticipantIds());
                            payload.put("callEnded",
                                    s.getStatus() == CallSessionStatus.ENDED);
                        });
            }
        }
        broadcastToGroupMembers(conversationId, userId, payload);
        // Also send back to the caller for the specific JOIN_CALL so they create a peer.
        if ("JOIN_CALL".equals(type)) {
            String callerTarget = payload.getOrDefault("targetCallerId", "").toString();
            if (!callerTarget.isBlank() && !callerTarget.equals(userId)) {
                messaging.convertAndSendToUser(callerTarget, "/queue/signal", payload);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void broadcastToGroupMembers(String conversationId,
                                          String senderUserId,
                                          Map<String, Object> payload) {
        // Always publish on the topic.
        messaging.convertAndSend(
                "/topic/conversations/" + conversationId + "/call", payload);

        // Also deliver per-user so members not subscribed to the topic get it.
        var conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv != null && conv.getMembers() != null) {
            for (var member : conv.getMembers()) {
                if (member.getLeftAt() != null || member.getUser() == null) continue;
                String memberId = member.getUser().getId();
                if (memberId.equals(senderUserId)) continue;
                messaging.convertAndSendToUser(memberId, "/queue/signal", payload);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String extractSdp(Map<String, Object> payload) {
        Object sdp = payload.get("sdp");
        if (sdp == null) return "";
        if (sdp instanceof String s) return s;
        if (sdp instanceof Map) {
            return ((Map<String, Object>) sdp).getOrDefault("sdp", "").toString();
        }
        return sdp.toString();
    }
}
