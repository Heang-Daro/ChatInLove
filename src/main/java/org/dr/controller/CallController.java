package org.dr.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.dr.dto.request.AcceptCallRequest;
import org.dr.model.CallSession;
import org.dr.model.User;
import org.dr.repository.CallSessionRepository;
import org.dr.service.CallSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/calls")
@CrossOrigin("*")
@RequiredArgsConstructor
@Slf4j
public class CallController {

	private final CallSessionService callSessionService;
	private final CallSessionRepository callSessionRepository;
	private final SimpMessagingTemplate messagingTemplate;

	/**
	 * Synchronous HTTP endpoint to accept a call. Guarantees caller's ringing stops
	 * immediately even if WebRTC or socket is reconnecting.
	 */
	@PostMapping("/accept/{sessionId}")
	public ResponseEntity<Map<String, Object>> acceptCall(@PathVariable String sessionId,
			@RequestBody(required = false) AcceptCallRequest request, @AuthenticationPrincipal User user) {

		if (user == null)
			return ResponseEntity.status(401).build();
		String calleeId = user.getId();

		Optional<CallSession> sessionOpt = callSessionService.acceptCall(sessionId, calleeId);
		if (sessionOpt.isEmpty()) {
			sessionOpt = callSessionRepository.findById(sessionId);
		}

		if (sessionOpt.isPresent()) {
			CallSession session = sessionOpt.get();
			String callerId = session.getCallerId();

			Map<String, Object> signal = new HashMap<>();
			signal.put("type", "CALL_ANSWER");
			signal.put("callSessionId", sessionId);
			signal.put("fromUserId", calleeId);
			signal.put("callerUserId", callerId);
			signal.put("status", "ACTIVE");

			// ប្រសិនបើ Flutter ផ្ញើ SDP Answer មកជាមួយ ត្រូវแนบចូលក្នុង Signal ភ្លាមៗ
			if (request != null && request.getSdp() != null) {
				signal.put("sdp", request.getSdp());
				log.info("[CallController] SDP Answer included in accept request for session {}", sessionId);
			}

			// Broadcast to caller's private signal queue
			messagingTemplate.convertAndSendToUser(callerId, "/queue/signal", signal);

			// Broadcast on room call topic
			messagingTemplate.convertAndSend("/topic/call/" + sessionId, signal);

			log.info("[CallController] Call session {} accepted by {} with SDP broadcasted", sessionId, calleeId);

			Map<String, Object> response = new HashMap<>();
			response.put("status", "SUCCESS");
			response.put("callSessionId", sessionId);
			response.put("callerId", callerId);
			return ResponseEntity.ok(response);
		}

		return ResponseEntity.notFound().build();
	}

	/**
	 * Synchronous HTTP endpoint to reject a call.
	 */
	@PostMapping("/reject/{sessionId}")
	public ResponseEntity<Map<String, Object>> rejectCall(@PathVariable String sessionId,
			@AuthenticationPrincipal User user) {

		if (user == null)
			return ResponseEntity.status(401).build();
		String calleeId = user.getId();

		Optional<CallSession> sessionOpt = callSessionService.rejectCall(sessionId, calleeId);
		if (sessionOpt.isEmpty()) {
			sessionOpt = callSessionRepository.findById(sessionId);
		}

		if (sessionOpt.isPresent()) {
			CallSession session = sessionOpt.get();
			String callerId = session.getCallerId();

			Map<String, Object> signal = new HashMap<>();
			signal.put("type", "CALL_REJECT");
			signal.put("callSessionId", sessionId);
			signal.put("fromUserId", calleeId);

			messagingTemplate.convertAndSendToUser(callerId, "/queue/signal", signal);
			messagingTemplate.convertAndSend("/topic/call/" + sessionId, signal);

			log.info("[CallController] Call session {} rejected by {}", sessionId, calleeId);

			return ResponseEntity.ok(Map.of("status", "REJECTED"));
		}

		return ResponseEntity.notFound().build();
	}

	/**
	 * Synchronous HTTP endpoint to hang up a call.
	 */
	@PostMapping("/hangup/{sessionId}")
	public ResponseEntity<Map<String, Object>> hangupCall(@PathVariable String sessionId,
			@AuthenticationPrincipal User user) {

		if (user == null)
			return ResponseEntity.status(401).build();
		String userId = user.getId();

		Optional<CallSession> sessionOpt = callSessionService.endCall(sessionId, userId);
		if (sessionOpt.isEmpty()) {
			sessionOpt = callSessionRepository.findById(sessionId);
		}

		if (sessionOpt.isPresent()) {
			CallSession session = sessionOpt.get();
			String targetId = session.getCallerId().equals(userId) ? session.getReceiverId() : session.getCallerId();

			Map<String, Object> signal = new HashMap<>();
			signal.put("type", "CALL_HANGUP");
			signal.put("callSessionId", sessionId);
			signal.put("fromUserId", userId);

			if (targetId != null) {
				messagingTemplate.convertAndSendToUser(targetId, "/queue/signal", signal);
			}
			messagingTemplate.convertAndSend("/topic/call/" + sessionId, signal);

			return ResponseEntity.ok(Map.of("status", "ENDED"));
		}

		return ResponseEntity.notFound().build();
	}

	@GetMapping("/session/{sessionId}")
	public ResponseEntity<CallSession> getCallSession(@PathVariable String sessionId) {
		return callSessionRepository.findById(sessionId).map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}
}
