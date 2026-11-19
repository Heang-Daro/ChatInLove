package org.dr.service;

import org.dr.model.CallSession;
import org.dr.model.enums.CallSessionStatus;
import org.dr.repository.CallSessionRepository;
import org.dr.repository.GroupCallSessionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class CallSessionServiceTest {

    @Test
    void acceptCallRejectsARequestFromTheWrongReceiver() {
        CallSessionRepository callRepo = mock(CallSessionRepository.class);
        GroupCallSessionRepository groupRepo = mock(GroupCallSessionRepository.class);
        CallSessionService service = new CallSessionService(callRepo, groupRepo);

        CallSession session = CallSession.builder()
                .id("call-1")
                .callerId("caller")
                .receiverId("callee")
                .status(CallSessionStatus.RINGING)
                .build();
        when(callRepo.findById("call-1")).thenReturn(Optional.of(session));

        assertTrue(service.acceptCall("call-1", "other-user").isEmpty());
        verify(callRepo, never()).save(any());
    }

    @Test
    void rejectCallRejectsASecondRequestAfterTheCallEnded() {
        CallSessionRepository callRepo = mock(CallSessionRepository.class);
        GroupCallSessionRepository groupRepo = mock(GroupCallSessionRepository.class);
        CallSessionService service = new CallSessionService(callRepo, groupRepo);

        CallSession session = CallSession.builder()
                .id("call-1")
                .callerId("caller")
                .receiverId("callee")
                .status(CallSessionStatus.ENDED)
                .build();
        when(callRepo.findById("call-1")).thenReturn(Optional.of(session));

        assertTrue(service.rejectCall("call-1", "callee").isEmpty());
        verify(callRepo, never()).save(any());
    }
}
