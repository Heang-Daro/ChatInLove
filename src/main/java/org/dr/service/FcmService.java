package org.dr.service;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase Cloud Messaging service for real-time high-priority push notifications.
 * Sends data-only messages with priority("HIGH") and ttl(0) so devices wake up instantly.
 */
@Service
@Slf4j
public class FcmService {

    /**
     * Send a high-priority incoming call push to a device.
     */
    public void sendIncomingCallPush(String fcmToken, String callerId, String callerName,
                                     String callSessionId, String conversationId, String callType, boolean isGroup) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("[FCM] No token provided for call push — skipping");
            return;
        }

        Map<String, String> data = new HashMap<>();
        data.put("type", "INCOMING_CALL");
        data.put("callId", callSessionId != null ? callSessionId : "");
        data.put("callSessionId", callSessionId != null ? callSessionId : "");
        data.put("conversationId", conversationId != null ? conversationId : "");
        data.put("fromUserId", callerId != null ? callerId : "");
        data.put("callerUserId", callerId != null ? callerId : "");
        data.put("callerName", callerName != null ? callerName : "Caller");
        data.put("callType", callType != null ? callType : "AUDIO");
        data.put("isVideo", String.valueOf("VIDEO".equalsIgnoreCase(callType)));
        data.put("isGroup", String.valueOf(isGroup));

        sendHighPriorityDataPush(fcmToken, data);
    }

    /**
     * Sends a data-only payload with HIGH priority and TTL(0).
     */
    public void sendHighPriorityDataPush(String fcmToken, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) return;

        try {
            Message msg = Message.builder()
                    .setToken(fcmToken)
                    .putAllData(data)
                    // Android: Priority HIGH + TTL 0 (deliver immediately or drop)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setTtl(0)
                            .build())
                    // iOS: High-priority background APNs config
                    .setApnsConfig(ApnsConfig.builder()
                            .putHeader("apns-priority", "10")
                            .putHeader("apns-push-type", "background")
                            .setAps(Aps.builder()
                                    .setContentAvailable(true)
                                    .build())
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(msg);
            log.info("[FCM] High-priority data push sent to {} → messageId={}", fcmToken, response);
        } catch (FirebaseMessagingException e) {
            log.warn("[FCM] Failed to send push to {}: {}", fcmToken, e.getMessage());
        }
    }

    public void sendDataPush(String fcmToken, Map<String, String> data) {
        sendHighPriorityDataPush(fcmToken, data);
    }
}
