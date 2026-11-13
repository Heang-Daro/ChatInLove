# ChatLoveIn Frontend Quick Start Guide

This guide explains how any frontend client (Flutter, React, Angular, Vue, Web, Mobile) connects seamlessly to the **`chat-love-in`** Spring Boot WebSocket & REST backend.

---

## 1. Server Configuration & Endpoints

- **Server Port:** `2026` (configured in `application.properties`)
- **Base REST URL:**
  - Localhost (Desktop/Web/iOS): `http://localhost:2026`
  - Android Emulator: `http://10.0.2.2:2026`
- **WebSocket STOMP Endpoint:**
  - Localhost: `ws://localhost:2026/chat`
  - Android Emulator: `ws://10.0.2.2:2026/chat`

---

## 2. Authentication API (`/api/auth`)

### 2.1 Register
- **URL:** `POST /api/auth/register`
- **Headers:** `Content-Type: application/json`
- **Request Body:**
  ```json
  {
    "name": "Alice Developer",
    "email": "alice@example.com",
    "password": "password123"
  }
  ```
- **Response (`201 Created`):**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": "user_id_guid",
    "name": "Alice Developer",
    "email": "alice@example.com"
  }
  ```

### 2.2 Login
- **URL:** `POST /api/auth/login`
- **Headers:** `Content-Type: application/json`
- **Request Body:**
  ```json
  {
    "email": "alice@example.com",
    "password": "password123"
  }
  ```
- **Response (`200 OK`):**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": "user_id_guid",
    "name": "Alice Developer",
    "email": "alice@example.com"
  }
  ```

---

## 3. WebSocket STOMP Connection Setup

### 3.1 STOMP `CONNECT` Frame
When connecting to `ws://localhost:2026/chat`, pass the JWT token in the `Authorization` header during the STOMP `CONNECT` frame:

```text
CONNECT
accept-version:1.1,1.0
heart-beat:10000,10000
Authorization: Bearer <JWT_TOKEN>
```

---

## 4. Real-Time Messaging Routes

### 4.1 Group Chat Channels
- **Send Message Destination:** `/app/chat.group/{roomId}`
- **Subscribe Topic Destination:** `/topic/room/{roomId}`
- **Request Payload (`MessageRequest`):**
  ```json
  {
    "content": "Hello everyone in room 1!",
    "messageType": "TEXT",
    "nickname": "Alice"
  }
  ```

### 4.2 Private (1-on-1 Direct) Messaging
- **Send Message Destination:** `/app/chat.private/{recipientId}`
- **Subscribe Topic Destination:** `/topic/private/{recipientId}`
- **Request Payload (`MessageRequest`):**
  ```json
  {
    "content": "Hi Bob, direct private message!",
    "messageType": "TEXT",
    "nickname": "Alice"
  }
  ```

### 4.3 Message Types
Supported values for `messageType`:
- `TEXT`
- `FILE`
- `VIDEO`
- `VOICE`
- `PHICTURE`
- `LOCATION`

---

## 5. Message Response Payload (`MessageResponse`)

When subscribed to `/topic/room/{roomId}` or `/topic/private/{recipientId}`, incoming frames contain:

```json
{
  "id": 1,
  "content": "Hello everyone in room 1!",
  "messageType": "TEXT",
  "senderId": "user_id_guid",
  "senderName": "Alice Developer",
  "senderImage": null,
  "nickname": "Alice",
  "recipientId": null,
  "recipientName": null,
  "chatType": "GROUP",
  "roomId": "1",
  "roomName": null,
  "read": false,
  "timeStamp": "2026-03-30T12:00:00"
}
```

---

## 6. Running Flutter Frontend (`chat_love_in`)

1. Start Spring Boot Backend (`ChatLoveInApplication.java` on port `2026`).
2. Run Flutter App:
   ```bash
   flutter pub get
   flutter run
   ```
3. Open the app, register/login, and start chatting!
