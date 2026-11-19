package org.dr.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
public class CallSessionRegistry {

    private final Map<String, ActiveCallSession> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public Optional<String> outgoingBlockReason(String userId, String requestingDeviceId) {
        ReentrantLock lock = lockFor(userId);
        lock.lock();
        try {
            ActiveCallSession existing = sessionsByUser.get(userId);
            if (existing == null) {
                return Optional.empty();
            }
            if (isExpired(existing)) {
                sessionsByUser.remove(userId);
                return Optional.empty();
            }
            if (existing.getStatus() != CallProgress.CALLING && existing.getStatus() != CallProgress.IN_CALL) {
                return Optional.empty();
            }
            if (existing.getStatus() == CallProgress.IN_CALL) {
                return Optional.of("ALREADY_IN_CALL");
            }
            if (requestingDeviceId != null && existing.getActiveDeviceId() != null
                    && !existing.getActiveDeviceId().equals(requestingDeviceId)) {
                return Optional.of("ACCOUNT_BUSY_ON_ANOTHER_DEVICE");
            }
            return Optional.of("ALREADY_IN_CALL");
        } finally {
            lock.unlock();
        }
    }

    public PrivateOfferDecision startPrivateOffer(String callerId,
                                                  String callerDeviceId,
                                                  String receiverId,
                                                  String sdpOffer,
                                                  Supplier<String> callIdSupplier) {
        return withLocks(callerId, receiverId, () -> {
            Optional<String> block = outgoingBlockReasonNoLock(callerId, callerDeviceId);
            if (block.isPresent()) {
                return PrivateOfferDecision.rejected(block.get());
            }

            ActiveCallSession receiverExisting = sessionsByUser.get(receiverId);
            if (receiverExisting != null && isExpired(receiverExisting)) {
                sessionsByUser.remove(receiverId);
                receiverExisting = null;
            }
            if (isPrivateCollision(receiverExisting, callerId)) {
                sessionsByUser.put(callerId, ActiveCallSession.builder()
                        .callId(receiverExisting.getCallId())
                        .callType(RegistryCallType.PRIVATE)
                        .activeDeviceId(callerDeviceId)
                        .peerOrGroupId(receiverId)
                        .status(CallProgress.RINGING)
                        .pendingOfferSdp(receiverExisting.getPendingOfferSdp())
                        .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(45))
                        .build());

                return PrivateOfferDecision.collision(
                        receiverExisting.getCallId(),
                        receiverExisting.getPendingOfferSdp(),
                        receiverId);
            }

            String callId = callIdSupplier.get();

            sessionsByUser.put(callerId, ActiveCallSession.builder()
                    .callId(callId)
                    .callType(RegistryCallType.PRIVATE)
                    .activeDeviceId(callerDeviceId)
                    .peerOrGroupId(receiverId)
                    .status(CallProgress.CALLING)
                    .pendingOfferSdp(sdpOffer)
                    .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(45))
                    .build());

            boolean waiting = receiverExisting != null
                    && (receiverExisting.getStatus() == CallProgress.IN_CALL
                    || receiverExisting.getStatus() == CallProgress.CALLING);
            if (!waiting) {
                sessionsByUser.put(receiverId, ActiveCallSession.builder()
                        .callId(callId)
                        .callType(RegistryCallType.PRIVATE)
                        .activeDeviceId(receiverExisting != null ? receiverExisting.getActiveDeviceId() : null)
                        .peerOrGroupId(callerId)
                        .status(CallProgress.RINGING)
                        .pendingOfferSdp(sdpOffer)
                        .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(45))
                        .build());
            }

            return PrivateOfferDecision.started(callId, waiting);
        });
    }

    public void markPrivateAnswered(String callId, String callerId, String calleeId, String calleeDeviceId) {
        withLocks(callerId, calleeId, () -> {
            ActiveCallSession caller = sessionsByUser.get(callerId);
            if (caller != null && callId.equals(caller.getCallId())) {
                caller.setStatus(CallProgress.IN_CALL);
                caller.setExpiresAt(null);
                sessionsByUser.put(callerId, caller);
            } else if (caller == null) {
                sessionsByUser.put(callerId, ActiveCallSession.builder()
                        .callId(callId)
                        .callType(RegistryCallType.PRIVATE)
                        .peerOrGroupId(calleeId)
                        .status(CallProgress.IN_CALL)
                        .build());
            }

            ActiveCallSession callee = sessionsByUser.get(calleeId);
            if (callee == null || callId.equals(callee.getCallId())) {
                sessionsByUser.put(calleeId, ActiveCallSession.builder()
                        .callId(callId)
                        .callType(RegistryCallType.PRIVATE)
                        .activeDeviceId(calleeDeviceId)
                        .peerOrGroupId(callerId)
                        .status(CallProgress.IN_CALL)
                        .build());
            }
            return null;
        });
    }

    public void markPrivateEnded(String callId, String userA, String userB) {
        withLocks(userA, userB, () -> {
            removeIfMatches(userA, callId);
            removeIfMatches(userB, callId);
            return null;
        });
    }

    public void markGroupInCall(String userId, String deviceId, String callId, String conversationId) {
        ReentrantLock lock = lockFor(userId);
        lock.lock();
        try {
            sessionsByUser.put(userId, ActiveCallSession.builder()
                    .callId(callId)
                    .callType(RegistryCallType.GROUP)
                    .activeDeviceId(deviceId)
                    .peerOrGroupId(conversationId)
                    .status(CallProgress.IN_CALL)
                    .build());
        } finally {
            lock.unlock();
        }
    }

    public void clearGroupIfMatches(String userId, String callId) {
        ReentrantLock lock = lockFor(userId);
        lock.lock();
        try {
            removeIfMatches(userId, callId);
        } finally {
            lock.unlock();
        }
    }

    public boolean clearIfOwnedByDevice(String userId, String disconnectedDeviceId) {
        ReentrantLock lock = lockFor(userId);
        lock.lock();
        try {
            ActiveCallSession active = sessionsByUser.get(userId);
            if (active == null || active.getActiveDeviceId() == null) {
                return false;
            }
            if (!active.getActiveDeviceId().equals(disconnectedDeviceId)) {
                return false;
            }
            sessionsByUser.remove(userId);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public Optional<ActiveCallSession> getActiveSession(String userId) {
        ActiveCallSession existing = sessionsByUser.get(userId);
        if (existing != null && isExpired(existing)) {
            sessionsByUser.remove(userId);
            return Optional.empty();
        }
        return Optional.ofNullable(existing);
    }

    private Optional<String> outgoingBlockReasonNoLock(String userId, String requestingDeviceId) {
        ActiveCallSession existing = sessionsByUser.get(userId);
        if (existing == null) {
            return Optional.empty();
        }
        if (isExpired(existing)) {
            sessionsByUser.remove(userId);
            return Optional.empty();
        }
        if (existing.getStatus() != CallProgress.CALLING && existing.getStatus() != CallProgress.IN_CALL) {
            return Optional.empty();
        }
        if (existing.getStatus() == CallProgress.IN_CALL) {
            return Optional.of("ALREADY_IN_CALL");
        }
        if (requestingDeviceId != null && existing.getActiveDeviceId() != null
                && !existing.getActiveDeviceId().equals(requestingDeviceId)) {
            return Optional.of("ACCOUNT_BUSY_ON_ANOTHER_DEVICE");
        }
        return Optional.of("ALREADY_IN_CALL");
    }

    private void removeIfMatches(String userId, String callId) {
        ActiveCallSession existing = sessionsByUser.get(userId);
        if (existing != null && callId.equals(existing.getCallId())) {
            sessionsByUser.remove(userId);
        }
    }

    private boolean isExpired(ActiveCallSession session) {
        return session.getExpiresAt() != null
                && session.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC));
    }

    private boolean isPrivateCollision(ActiveCallSession receiverExisting, String callerId) {
        return receiverExisting != null
                && receiverExisting.getCallType() == RegistryCallType.PRIVATE
                && receiverExisting.getStatus() == CallProgress.CALLING
                && callerId.equals(receiverExisting.getPeerOrGroupId())
                && receiverExisting.getPendingOfferSdp() != null
                && !receiverExisting.getPendingOfferSdp().isBlank();
    }

    private <T> T withLocks(String userA, String userB, Supplier<T> work) {
        List<String> ordered = userA.compareTo(userB) <= 0
                ? List.of(userA, userB)
                : List.of(userB, userA);
        ReentrantLock first = lockFor(ordered.get(0));
        ReentrantLock second = lockFor(ordered.get(1));

        first.lock();
        second.lock();
        try {
            return work.get();
        } finally {
            second.unlock();
            first.unlock();
        }
    }

    private ReentrantLock lockFor(String userId) {
        return userLocks.computeIfAbsent(userId, ignored -> new ReentrantLock());
    }

    public enum RegistryCallType {
        PRIVATE,
        GROUP
    }

    public enum CallProgress {
        CALLING,
        RINGING,
        IN_CALL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveCallSession {
        private String callId;
        private RegistryCallType callType;
        private String activeDeviceId;
        private String peerOrGroupId;
        private CallProgress status;
        private String pendingOfferSdp;
        private LocalDateTime expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrivateOfferDecision {
        private Outcome outcome;
        private String reason;
        private String callId;
        private String peerPendingOfferSdp;
        private String peerUserId;
        private boolean callWaiting;

        public static PrivateOfferDecision rejected(String reason) {
            return PrivateOfferDecision.builder()
                    .outcome(Outcome.REJECTED)
                    .reason(reason)
                    .build();
        }

        public static PrivateOfferDecision collision(String callId, String pendingSdp, String peerUserId) {
            return PrivateOfferDecision.builder()
                    .outcome(Outcome.COLLISION)
                    .callId(callId)
                    .peerPendingOfferSdp(pendingSdp)
                    .peerUserId(peerUserId)
                    .build();
        }

        public static PrivateOfferDecision started(String callId, boolean callWaiting) {
            return PrivateOfferDecision.builder()
                    .outcome(Outcome.STARTED)
                    .callId(callId)
                    .callWaiting(callWaiting)
                    .build();
        }
    }

    public enum Outcome {
        STARTED,
        COLLISION,
        REJECTED
    }
}