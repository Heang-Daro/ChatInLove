package org.dr.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Firebase Cloud Messaging service for push notifications.
 *
 * To send push notifications to offline users, the Flutter client must register
 * its FCM token and send it to the server (e.g. POST /api/auth/fcm-token).
 * This service uses that stored token to deliver a data-only push so the app
 * can display the incoming call UI from the background.
 */
@Service
@Slf4j
public class FcmService {

    /**
     * Send a high-priority incoming call push to a device.
     *
     * @param fcmToken   the recipient's device FCM registration token
     * @param callerId   display name or ID of the caller
     * @param callSessionId  the call session ID so the app can answer/reject
     * @param callType   "AUDIO" or "VIDEO"
     */
    public void sendIncomingCallPush(String fcmToken, String callerId,
                                     String callSessionId, String callType) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("[FCM] No token for user — skipping push");
            return;
        }
        try {
            Message msg = Message.builder()
                    .setToken(fcmToken)
                    // Data-only payload so the Flutter app can handle it in background.
                    .putAllData(Map.of(
                            "type",          "INCOMING_CALL",
                            "callerId",      callerId,
                            "callSessionId", callSessionId,
                            "callType",      callType
                    ))
                    // High-priority so the OS wakes the app.
                    .setAndroidConfig(com.google.firebase.messaging.AndroidConfig.builder()
                            .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)
                            .build())
                    .setApnsConfig(com.google.firebase.messaging.ApnsConfig.builder()
                            .setAps(com.google.firebase.messaging.Aps.builder()
                                    .setContentAvailable(true)
                                    .build())
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(msg);
            log.info("[FCM] Incoming call push sent to {} → messageId={}", fcmToken, response);
        } catch (FirebaseMessagingException e) {
            log.warn("[FCM] Failed to send call push: {}", e.getMessage());
        }
    }

    /**
     * Send a generic data push (e.g. group call invite in chat).
     */
    public void sendDataPush(String fcmToken, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) return;
        try {
            Message msg = Message.builder()
                    .setToken(fcmToken)
                    .putAllData(data)
                    .setAndroidConfig(com.google.firebase.messaging.AndroidConfig.builder()
                            .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)
                            .build())
                    .build();
            FirebaseMessaging.getInstance().send(msg);
        } catch (FirebaseMessagingException e) {
            log.warn("[FCM] Failed to send data push: {}", e.getMessage());
        }
    }
}
