package org.dr.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.model.CallSession;
import org.dr.model.GroupCallSession;
import org.dr.model.enums.CallSessionStatus;
import org.dr.model.enums.MessageType;
import org.dr.repository.ConversationRepository;
import org.dr.repository.UserRepository;
import org.dr.service.CallSessionService;
import org.dr.service.CallSessionRegistry;
import org.dr.service.FcmService;
import org.dr.service.MessageService;
import org.dr.service.PresenceService;
import org.dr.dto.request.SendMessageRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
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
    private final UserRepository          userRepository;
    private final CallSessionService      callSessionService;
    private final CallSessionRegistry     callSessionRegistry;
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
            @Header(name = "simpSessionId", required = false) String websocketSessionId,
            Principal principal) {

        if (principal == null) {
            log.warn("[Signal] unauthenticated message dropped");
            return;
        }

        String callerId = principal.getName();
        String callerDeviceId = extractDeviceId(payload, websocketSessionId);
        payload.put("fromUserId", callerId);  // server-stamped, not client-supplied

        String msgType = payload.getOrDefault("type", "UNKNOWN").toString();
        log.debug("[Signal] {} {} → {}", msgType, callerId, targetUserId);

        switch (msgType) {
            case "CALL_OFFER" -> handleCallOffer(callerId, callerDeviceId, targetUserId, payload);
            case "CALL_ANSWER" -> handleCallAnswer(callerId, callerDeviceId, targetUserId, payload);
            case "CALL_REJECT" -> handleCallReject(callerId, callerDeviceId, targetUserId, payload);
            case "CALL_CANCEL",
                 "CALL_HANGUP" -> handleCallEnd(callerId, targetUserId, payload);
            default -> messaging.convertAndSendToUser(targetUserId, "/queue/signal", payload);
        }
    }

    // ── CALL_OFFER ────────────────────────────────────────────────────────────

    private void handleCallOffer(String callerId, String callerDeviceId, String receiverId,
                                  Map<String, Object> payload) {
        String callType   = payload.getOrDefault("callType", "AUDIO").toString();
        String sdpOffer   = extractSdp(payload);
        String convId     = payload.getOrDefault("conversationId", "").toString();

        CallSessionRegistry.PrivateOfferDecision decision = callSessionRegistry.startPrivateOffer(
                callerId,
                callerDeviceId,
                receiverId,
                sdpOffer,
                () -> callSessionService.createCallSession(callerId, receiverId, sdpOffer, callType).getId());

        if (decision.getOutcome() == CallSessionRegistry.Outcome.REJECTED) {
            sendToSingleDevice(callerId, callerDeviceId, Map.of(
                    "type", "CALL_REJECTED_BUSY",
                    "reason", decision.getReason(),
                    "targetUserId", receiverId
            ));
            return;
        }

        if (decision.getOutcome() == CallSessionRegistry.Outcome.COLLISION) {
            sendToSingleDevice(callerId, callerDeviceId, Map.of(
                    "type", "COLLISION_AUTO_ACCEPT",
                    "callSessionId", decision.getCallId(),
                    "fromUserId", decision.getPeerUserId(),
                    "peerPendingOfferSdp", decision.getPeerPendingOfferSdp(),
                    "callType", callType
            ));
            return;
        }

        String callSessionId = decision.getCallId();
        payload.put("callSessionId", callSessionId);
        if (decision.isCallWaiting()) {
            payload.put("isCallWaiting", true);
        }

        String callerName = callerId;
        var callerOpt = userRepository.findById(callerId);
        if (callerOpt.isPresent()) {
            callerName = callerOpt.get().getUsername();
        }

        // Deliver via STOMP socket
        messaging.convertAndSendToUser(receiverId, "/queue/signal", payload);

        // Send high-priority FCM data push to wake up device in killed/background state
        var receiverOpt = userRepository.findById(receiverId);
        if (receiverOpt.isPresent() && receiverOpt.get().getFcmToken() != null) {
            String fcmToken = receiverOpt.get().getFcmToken();
            log.info("[Signal] Sending high-priority FCM call push to receiver {} (token={})", receiverId, fcmToken);
            fcmService.sendIncomingCallPush(fcmToken, callerId, callerName, callSessionId, convId, callType, false);
        }
    }

    // ── CALL_ANSWER ───────────────────────────────────────────────────────────

    private void handleCallAnswer(String receiverId, String receiverDeviceId, String callerId,
                                   Map<String, Object> payload) {
        String sessionId = payload.getOrDefault("callSessionId", "").toString();
        if (!sessionId.isBlank()) {
            callSessionService.acceptCall(sessionId, receiverId);
            callSessionRegistry.markPrivateAnswered(sessionId, callerId, receiverId, receiverDeviceId);
            cancelRingingOnOtherDevices(receiverId, receiverDeviceId, sessionId, callerId);
        }
        messaging.convertAndSendToUser(callerId, "/queue/signal", payload);
        if (!sessionId.isBlank()) {
            messaging.convertAndSend("/topic/call/" + sessionId, payload);
        }
    }

    // ── CALL_REJECT ───────────────────────────────────────────────────────────

    private void handleCallReject(String receiverId, String receiverDeviceId, String callerId,
                                   Map<String, Object> payload) {
        String sessionId = payload.getOrDefault("callSessionId", "").toString();
        if (!sessionId.isBlank()) {
            callSessionService.rejectCall(sessionId, receiverId);
            callSessionRegistry.markPrivateEnded(sessionId, callerId, receiverId);
            cancelRingingOnOtherDevices(receiverId, receiverDeviceId, sessionId, callerId);
        }
        messaging.convertAndSendToUser(callerId, "/queue/signal", payload);
    }

    // ── CALL_CANCEL / CALL_HANGUP ─────────────────────────────────────────────

    private void handleCallEnd(String userId, String otherUserId,
                                Map<String, Object> payload) {
        String sessionId = payload.getOrDefault("callSessionId", "").toString();
        if (!sessionId.isBlank()) {
            callSessionService.endCall(sessionId, userId);
            callSessionRegistry.markPrivateEnded(sessionId, userId, otherUserId);
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
            @Header(name = "simpSessionId", required = false) String websocketSessionId,
            Principal principal) {

        if (principal == null) {
            log.warn("[Signal] unauthenticated group message dropped");
            return;
        }

        String callerId = principal.getName();
        String callerDeviceId = extractDeviceId(payload, websocketSessionId);
        payload.put("fromUserId", callerId);
        payload.put("conversationId", conversationId);

        String msgType = payload.getOrDefault("type", "UNKNOWN").toString();
        log.debug("[Signal] group {} {} on conv {}", msgType, callerId, conversationId);

        if ("GROUP_CALL_STARTED".equals(msgType)) {
            handleGroupCallStarted(callerId, callerDeviceId, conversationId, payload);
        } else if ("LEAVE_CALL".equals(msgType) || "JOIN_CALL".equals(msgType)) {
            handleGroupCallMembership(callerId, callerDeviceId, conversationId, msgType, payload);
        } else {
            broadcastToGroupMembers(conversationId, callerId, payload);
        }
    }

    // ── GROUP_CALL_STARTED ────────────────────────────────────────────────────

    private void handleGroupCallStarted(String callerId, String callerDeviceId, String conversationId,
                                         Map<String, Object> payload) {
        var blockReason = callSessionRegistry.outgoingBlockReason(callerId, callerDeviceId);
        if (blockReason.isPresent()) {
            sendToSingleDevice(callerId, callerDeviceId, Map.of(
                    "type", "CALL_REJECTED_BUSY",
                    "reason", blockReason.get(),
                    "conversationId", conversationId
            ));
            return;
        }

        var existingActive = callSessionService.findActiveGroupCall(conversationId);
        if (existingActive.isPresent()) {
            GroupCallSession live = existingActive.get();
            callSessionRegistry.markGroupInCall(callerId, callerDeviceId, live.getId(), conversationId);

            Map<String, Object> routed = new HashMap<>(payload);
            routed.put("type", "GROUP_CALL_STARTED");
            routed.put("callSessionId", live.getId());
            routed.put("reusedSession", true);
            routed.put("activeParticipants", live.getActiveParticipantIds());
            sendToSingleDevice(callerId, callerDeviceId, routed);
            return;
        }

        String callType = payload.getOrDefault("callType", "AUDIO").toString();

        // Create persistent group call session.
        GroupCallSession gcs = callSessionService.createGroupCallSession(
                conversationId, callerId, callType);

        payload.put("callSessionId", gcs.getId());
        payload.put("activeParticipants", Set.of(callerId));
        callSessionRegistry.markGroupInCall(callerId, callerDeviceId, gcs.getId(), conversationId);

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

    private void handleGroupCallMembership(String userId, String userDeviceId, String conversationId,
                                            String type, Map<String, Object> payload) {
        String sessionId = payload.getOrDefault("callSessionId", "").toString();
        if (!sessionId.isBlank()) {
            if ("JOIN_CALL".equals(type)) {
                callSessionService.participantJoined(sessionId, userId)
                        .ifPresent(s -> {
                            payload.put("activeParticipants", s.getActiveParticipantIds());
                            callSessionRegistry.markGroupInCall(userId, userDeviceId, sessionId, conversationId);
                        });
            } else {
                callSessionService.participantLeft(sessionId, userId)
                        .ifPresent(s -> {
                            payload.put("activeParticipants", s.getActiveParticipantIds());
                            payload.put("callEnded",
                                    s.getStatus() == CallSessionStatus.ENDED);
                            callSessionRegistry.clearGroupIfMatches(userId, sessionId);
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

    private String extractDeviceId(Map<String, Object> payload, String websocketSessionId) {
        String payloadDeviceId = payload.getOrDefault("deviceId", "").toString();
        if (!payloadDeviceId.isBlank()) {
            return payloadDeviceId;
        }
        return websocketSessionId;
    }

    private void cancelRingingOnOtherDevices(String userId,
                                             String acceptedDeviceId,
                                             String callSessionId,
                                             String peerUserId) {
        if (acceptedDeviceId == null || acceptedDeviceId.isBlank()) {
            return;
        }
        for (String sessionId : presenceService.getActiveSessionIds(userId)) {
            if (acceptedDeviceId.equals(sessionId)) {
                continue;
            }
            sendToSingleDevice(userId, sessionId, Map.of(
                    "type", "CANCEL_RINGING",
                    "callSessionId", callSessionId,
                    "peerUserId", peerUserId,
                    "acceptedByDeviceId", acceptedDeviceId
            ));
        }
    }

    private void sendToSingleDevice(String userId, String sessionId, Map<String, Object> payload) {
        if (sessionId == null || sessionId.isBlank()) {
            messaging.convertAndSendToUser(userId, "/queue/signal", payload);
            return;
        }
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId);
        headers.setLeaveMutable(true);
        messaging.convertAndSendToUser(userId, "/queue/signal", payload, headers.getMessageHeaders());
    }
}
