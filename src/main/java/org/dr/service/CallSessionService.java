package org.dr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.model.CallSession;
import org.dr.model.GroupCallSession;
import org.dr.model.enums.CallSessionStatus;
import org.dr.model.enums.CallType;
import org.dr.repository.CallSessionRepository;
import org.dr.repository.GroupCallSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for 1-to-1 and group call session lifecycle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallSessionService {

    private final CallSessionRepository callSessionRepo;
    private final GroupCallSessionRepository groupCallSessionRepo;

    // ── 1-to-1 call sessions ──────────────────────────────────────────────────

    @Transactional
    public CallSession createCallSession(String callerId, String receiverId,
                                          String sdpOffer, String callType) {
        CallSession session = CallSession.builder()
                .callerId(callerId)
                .receiverId(receiverId)
                .sdpOffer(sdpOffer)
                .status(CallSessionStatus.RINGING)
                .callType(CallType.valueOf(callType.toUpperCase()))
                .build();
        return callSessionRepo.save(session);
    }

    @Transactional
    public Optional<CallSession> acceptCall(String sessionId, String receiverId) {
        return callSessionRepo.findById(sessionId).map(s -> {
            if (!s.getReceiverId().equals(receiverId)) return s;
            if (s.getStatus() != CallSessionStatus.RINGING) return s;
            s.setStatus(CallSessionStatus.ACTIVE);
            return callSessionRepo.save(s);
        });
    }

    @Transactional
    public Optional<CallSession> endCall(String sessionId, String userId) {
        return callSessionRepo.findById(sessionId).map(s -> {
            if (!s.getCallerId().equals(userId) && !s.getReceiverId().equals(userId)) return s;
            if (s.getStatus() == CallSessionStatus.ENDED) return s;
            s.setStatus(CallSessionStatus.ENDED);
            s.setEndedAt(LocalDateTime.now(ZoneOffset.UTC));
            return callSessionRepo.save(s);
        });
    }

    @Transactional
    public Optional<CallSession> rejectCall(String sessionId, String receiverId) {
        return callSessionRepo.findById(sessionId).map(s -> {
            if (!s.getReceiverId().equals(receiverId)) return s;
            if (s.getStatus() != CallSessionStatus.RINGING) return s;
            s.setStatus(CallSessionStatus.REJECTED);
            s.setEndedAt(LocalDateTime.now(ZoneOffset.UTC));
            return callSessionRepo.save(s);
        });
    }

    /** Called when a user (re)connects to check for a missed ringing call. */
    @Transactional(readOnly = true)
    public List<CallSession> findPendingCallsForReceiver(String receiverId) {
        return callSessionRepo.findPendingForReceiver(receiverId,
                LocalDateTime.now(ZoneOffset.UTC));
    }

    // ── Group call sessions ───────────────────────────────────────────────────

    @Transactional
    public GroupCallSession createGroupCallSession(String conversationId,
                                                    String callerId, String callType) {
        GroupCallSession session = GroupCallSession.builder()
                .conversationId(conversationId)
                .callerId(callerId)
                .status(CallSessionStatus.ACTIVE)
                .callType(CallType.valueOf(callType.toUpperCase()))
                .build();
        session.getActiveParticipantIds().add(callerId);
        return groupCallSessionRepo.save(session);
    }

    @Transactional
    public Optional<GroupCallSession> participantJoined(String sessionId, String userId) {
        return groupCallSessionRepo.findById(sessionId).map(s -> {
            s.getActiveParticipantIds().add(userId);
            return groupCallSessionRepo.save(s);
        });
    }

    @Transactional
    public Optional<GroupCallSession> participantLeft(String sessionId, String userId) {
        return groupCallSessionRepo.findById(sessionId).map(s -> {
            s.getActiveParticipantIds().remove(userId);
            if (s.getActiveParticipantIds().isEmpty()) {
                s.setStatus(CallSessionStatus.ENDED);
                s.setEndedAt(LocalDateTime.now(ZoneOffset.UTC));
            }
            return groupCallSessionRepo.save(s);
        });
    }

    public Optional<GroupCallSession> findActiveGroupCall(String conversationId) {
        return groupCallSessionRepo
                .findFirstByConversationIdAndStatusOrderByCreatedAtDesc(
                        conversationId, CallSessionStatus.ACTIVE);
    }

    // ── Scheduled cleanup ─────────────────────────────────────────────────────

    /** Auto-expire RINGING calls that passed their ring window (runs every 30 s). */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void expireRingingCalls() {
        int count = callSessionRepo.expireRingingCalls(LocalDateTime.now(ZoneOffset.UTC));
        if (count > 0) log.info("[CallSession] Expired {} ringing call(s)", count);
    }
}
