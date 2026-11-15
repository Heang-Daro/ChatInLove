package org.dr.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.model.CallSession;
import org.dr.service.CallSessionService;
import org.dr.service.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Tracks WebSocket connect/disconnect events for user presence and
 * missed-call recovery.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceEventListener {

    private final PresenceService presenceService;
    private final CallSessionService callSessionService;
    private final SimpMessagingTemplate messaging;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = sha.getUser();
        if (principal == null) return;

        String userId    = principal.getName();
        String sessionId = sha.getSessionId();

        presenceService.onConnect(userId, sessionId);

        // ── Missed call recovery ─────────────────────────────────────────
        // If there is a still-ringing call for this user, deliver it now.
        List<CallSession> pending = callSessionService.findPendingCallsForReceiver(userId);
        if (!pending.isEmpty()) {
            CallSession session = pending.get(0); // most recent
            log.info("[Presence] Delivering missed call {} to user {} on reconnect",
                    session.getId(), userId);
            messaging.convertAndSendToUser(userId, "/queue/signal", Map.of(
                    "type",          "CALL_OFFER",
                    "callSessionId", session.getId(),
                    "fromUserId",    session.getCallerId(),
                    "callType",      session.getCallType().name(),
                    "sdp",           session.getSdpOffer() != null
                                        ? session.getSdpOffer() : "",
                    "recovered",     true  // tells Flutter this is a recovered offer
            ));
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = sha.getUser();
        if (principal == null) return;

        presenceService.onDisconnect(principal.getName(), sha.getSessionId());
    }
}
