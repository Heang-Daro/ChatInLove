package org.dr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.dto.response.UserPresenceResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory presence tracker.
 *
 * Keeps a set of currently-connected WebSocket sessions per userId (a user can
 * have multiple active sessions on different devices). A user is ONLINE as long
 * as at least one session is connected. lastSeen is updated on every disconnect.
 *
 * Status updates are broadcast to /topic/presence so all subscribers see
 * real-time online/offline changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final SimpMessagingTemplate messaging;

    // userId → Set<sessionId>
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    // userId → last-seen epoch millis
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();

    // ── Connect ──────────────────────────────────────────────────────────────

    public void onConnect(String userId, String sessionId) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        log.debug("[Presence] {} connected (session {})", userId, sessionId);
        broadcastStatus(userId, true);
    }

    // ── Disconnect ────────────────────────────────────────────────────────────

    public void onDisconnect(String userId, String sessionId) {
        Set<String> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
                lastSeen.put(userId, Instant.now().toEpochMilli());
                log.debug("[Presence] {} went offline", userId);
                broadcastStatus(userId, false);
            }
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isOnline(String userId) {
        Set<String> s = userSessions.get(userId);
        return s != null && !s.isEmpty();
    }

    public Set<String> onlineUserIds() {
        return userSessions.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public Long getLastSeen(String userId) {
        return lastSeen.get(userId);
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    private void broadcastStatus(String userId, boolean online) {
        messaging.convertAndSend("/topic/presence",
                Map.of("userId", userId,
                       "online", online,
                       "lastSeen", online ? 0L : lastSeen.getOrDefault(userId, 0L)));
    }
}
