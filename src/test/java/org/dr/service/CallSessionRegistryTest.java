package org.dr.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class CallSessionRegistryTest {

    @Test
    void expiredPrivateSessionDoesNotBlockANewCall() {
        CallSessionRegistry registry = new CallSessionRegistry();
        CallSessionRegistry.PrivateOfferDecision first = registry.startPrivateOffer(
                "caller", "device-a", "callee", "offer", () -> "old-call");
        assertEquals(CallSessionRegistry.Outcome.STARTED, first.getOutcome());

        registry.getActiveSession("caller").orElseThrow()
                .setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));

        CallSessionRegistry.PrivateOfferDecision decision = registry.startPrivateOffer(
                "caller", "device-a", "other-callee", "offer", () -> "new-call");

        assertEquals(CallSessionRegistry.Outcome.STARTED, decision.getOutcome());
        assertEquals("new-call",
                registry.getActiveSession("caller").orElseThrow().getCallId());
    }

    @Test
    void endedPrivateSessionIsRemovedForBothUsers() {
        CallSessionRegistry registry = new CallSessionRegistry();
        registry.startPrivateOffer("caller", "device-a", "callee", "offer", () -> "call-1");

        registry.markPrivateEnded("call-1", "caller", "callee");

        assertTrue(registry.getActiveSession("caller").isEmpty());
        assertTrue(registry.getActiveSession("callee").isEmpty());
    }

    @Test
    void answerMovesMatchingSessionsToInCall() {
        CallSessionRegistry registry = new CallSessionRegistry();
        registry.startPrivateOffer("caller", "device-a", "callee", "offer", () -> "call-1");

        registry.markPrivateAnswered("call-1", "caller", "callee", "device-b");

        assertEquals(CallSessionRegistry.CallProgress.IN_CALL,
                registry.getActiveSession("caller").orElseThrow().getStatus());
        assertEquals(CallSessionRegistry.CallProgress.IN_CALL,
                registry.getActiveSession("callee").orElseThrow().getStatus());
    }
}
