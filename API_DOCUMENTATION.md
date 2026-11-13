# chatInLove - Backend API Documentation

Complete backend API specification for the **chatInLove** chat application with real-time messaging, device sessions, and 1-to-1 calls.

## Table of Contents
1. [Authentication Flow](#authentication-flow)
2. [REST API Endpoints](#rest-api-endpoints)
3. [WebSocket/STOMP Endpoints](#websocketstomp-endpoints)
4. [Request/Response Examples](#requestresponse-examples)
5. [Error Handling](#error-handling)
6. [Setup Instructions](#setup-instructions)

---

## Authentication Flow

### 1. Register
**Endpoint:** `POST /api/auth/register`

**Request:**
```json
{
  "email": "user@example.com",
  "username": "john_doe",
  "password": "SecurePassword123"
}
```

**Response (201 Created):**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

### 2. Login
**Endpoint:** `POST /api/auth/login`

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123"
}
```

**Response (200 OK):**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

### 3. Refresh Token
**Endpoint:** `POST /api/auth/refresh`

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK):**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

### 4. Get Current User
**Endpoint:** `GET /api/auth/principal`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "username": "john_doe",
  "role": "USER",
  "createdAt": "2026-10-07T10:30:00"
}
```

### 5. Get Device Sessions
**Endpoint:** `GET /api/auth/devices`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Response (200 OK):**
```json
[
  {
    "id": "device-session-id-1",
    "deviceName": "Chrome",
    "deviceType": "WEB",
    "ipAddress": "192.168.1.1",
    "lastActiveAt": "2026-10-07T14:20:00",
    "expiresAt": "2026-10-14T10:30:00",
    "revoked": false
  }
]
```

### 6. Logout
**Endpoint:** `POST /api/auth/logout`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Response (204 No Content)**

### 7. Logout All Devices
**Endpoint:** `POST /api/auth/logout-all`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Response (204 No Content)**

### 8. Revoke Device Session
**Endpoint:** `DELETE /api/auth/devices/{deviceId}`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Response (204 No Content)**

---

## REST API Endpoints

### Conversations

#### Get All Conversations (Paginated)
**Endpoint:** `GET /api/conversations?page=0&size=20`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Query Parameters:**
- `page` (default: 0) - Page number
- `size` (default: 20) - Items per page
- `sort` - Default: `lastMessageAt` DESC

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": "conv-uuid-1",
      "type": "PRIVATE",
      "name": null,
      "description": null,
      "createdAt": "2026-10-07T10:00:00",
      "lastMessageAt": "2026-10-07T14:30:00",
      "members": [
        {
          "userId": "user-1",
          "username": "john_doe",
          "role": "MEMBER",
          "joinedAt": "2026-10-07T10:00:00",
          "leftAt": null
        }
      ]
    }
  ],
  "totalPages": 1,
  "totalElements": 1,
  "size": 20,
  "number": 0
}
```

#### Get Specific Conversation
**Endpoint:** `GET /api/conversations/{conversationId}`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Response (200 OK):** Same as individual conversation object above

#### Create Private Conversation
**Endpoint:** `POST /api/conversations/private`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Request:**
```json
{
  "recipientId": "recipient-user-id"
}
```

**Response (201 Created):** Conversation object

---

### Messages

#### Get Messages from Conversation (Cursor Pagination)
**Endpoint:** `GET /api/conversations/{conversationId}/messages?size=50&before=2026-10-07T14:30:00`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Query Parameters:**
- `size` (default: 50) - Messages per page
- `before` - ISO DateTime cursor for pagination (get messages before this time)
- `sort` - Default: `createdAt` DESC

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": "msg-uuid-1",
      "conversationId": "conv-uuid-1",
      "senderId": "user-1",
      "senderUsername": "john_doe",
      "type": "TEXT",
      "content": "Hello!",
      "isRead": true,
      "createdAt": "2026-10-07T14:20:00",
      "editedAt": null,
      "deletedAt": null,
      "relatedCallId": null
    }
  ],
  "totalPages": 1,
  "totalElements": 1,
  "size": 50,
  "number": 0
}
```

#### Send Message
**Endpoint:** `POST /api/conversations/{conversationId}/messages`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Request:**
```json
{
  "content": "Hello, this is my message!"
}
```

**Response (201 Created):** Message object (as shown above)

#### Edit Message
**Endpoint:** `PUT /api/conversations/messages/{messageId}`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Request:**
```json
{
  "content": "Updated message content"
}
```

**Response (200 OK):** Updated message object

#### Delete Message (Soft Delete)
**Endpoint:** `DELETE /api/conversations/messages/{messageId}`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Response (204 No Content)**

#### Mark Message as Read
**Endpoint:** `POST /api/conversations/{conversationId}/messages/{messageId}/read`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Response (204 No Content)**

#### Mark All Messages as Read
**Endpoint:** `POST /api/conversations/{conversationId}/read-all`

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Response (204 No Content)**

---

## WebSocket/STOMP Endpoints

### Connection
**Endpoint:** `ws://localhost:8080/ws`

**Headers (STOMP CONNECT):**
```
Authorization: Bearer {accessToken}
```

**Example (JavaScript):**
```javascript
const client = new StompJs.Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: {
    'Authorization': `Bearer ${accessToken}`
  },
  onConnect: () => {
    console.log('Connected');
  }
});
client.activate();
```

### Send Message
**Destination:** `/app/chat.send/{conversationId}`

**Payload:**
```json
{
  "content": "Real-time message!"
}
```

**Broadcast Destination:** `/topic/conversations/{conversationId}`

**Broadcast Response:**
```json
{
  "id": "msg-uuid-1",
  "conversationId": "conv-uuid-1",
  "senderId": "user-1",
  "senderUsername": "john_doe",
  "type": "TEXT",
  "content": "Real-time message!",
  "isRead": false,
  "createdAt": "2026-10-07T14:25:00",
  "editedAt": null,
  "deletedAt": null,
  "relatedCallId": null
}
```

**Example (JavaScript):**
```javascript
// Send
client.publish({
  destination: '/app/chat.send/conv-uuid-1',
  body: JSON.stringify({
    content: 'Hello WebSocket!'
  })
});

// Subscribe
client.subscribe('/topic/conversations/conv-uuid-1', (message) => {
  const msg = JSON.parse(message.body);
  console.log('New message:', msg);
});
```

### Typing Notification
**Destination:** `/app/chat.typing/{conversationId}`

**Payload:** (empty body, just send)

**Broadcast Destination:** `/topic/conversations/{conversationId}/typing`

**Broadcast Response:**
```json
{
  "userId": "user-1",
  "username": "john_doe",
  "conversationId": "conv-uuid-1",
  "isTyping": true
}
```

**Example (JavaScript):**
```javascript
// Subscribe to typing
client.subscribe('/topic/conversations/conv-uuid-1/typing', (message) => {
  const typing = JSON.parse(message.body);
  console.log(`${typing.username} is typing...`);
});

// Send typing notification
client.publish({
  destination: '/app/chat.typing/conv-uuid-1',
  body: ''
});
```

### Personal Notifications
**Subscribe To:** `/user/queue/notifications`

**Used For:**
- Incoming group invites
- Invite responses
- Chat invitations (future calls feature)
- Other real-time notifications

**Example (JavaScript):**
```javascript
client.subscribe('/user/queue/notifications', (message) => {
  const notification = JSON.parse(message.body);
  console.log('Notification:', notification);
});
```

---

## Request/Response Examples

### Complete Authentication Flow Example

```javascript
// 1. Register
const registerResponse = await fetch('http://localhost:8080/api/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: 'user@example.com',
    username: 'john_doe',
    password: 'SecurePassword123'
  })
});

const { accessToken, refreshToken } = await registerResponse.json();

// 2. Store tokens in localStorage
localStorage.setItem('accessToken', accessToken);
localStorage.setItem('refreshToken', refreshToken);

// 3. Use accessToken for all requests
const headers = {
  'Authorization': `Bearer ${accessToken}`,
  'Content-Type': 'application/json'
};

// 4. When token expires, refresh it
const refreshResponse = await fetch('http://localhost:8080/api/auth/refresh', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ refreshToken })
});

const { accessToken: newAccessToken } = await refreshResponse.json();
localStorage.setItem('accessToken', newAccessToken);
```

### Complete Chat Flow Example

```javascript
import * as StompJs from '@stomp/stompjs';

class ChatClient {
  constructor(accessToken) {
    this.accessToken = accessToken;
    this.client = new StompJs.Client({
      brokerURL: 'ws://localhost:8080/ws',
      connectHeaders: {
        'Authorization': `Bearer ${accessToken}`
      }
    });
    this.client.onConnect = () => this.onConnected();
    this.client.onDisconnect = () => console.log('Disconnected');
  }

  connect() {
    this.client.activate();
  }

  onConnected() {
    console.log('WebSocket connected');
    
    // Subscribe to a conversation
    this.subscribeToConversation('conv-uuid-1');
    this.subscribeToTyping('conv-uuid-1');
    this.subscribeToNotifications();
  }

  subscribeToConversation(conversationId) {
    this.client.subscribe(`/topic/conversations/${conversationId}`, (message) => {
      const msg = JSON.parse(message.body);
      this.onNewMessage(msg);
    });
  }

  subscribeToTyping(conversationId) {
    this.client.subscribe(`/topic/conversations/${conversationId}/typing`, (message) => {
      const typing = JSON.parse(message.body);
      console.log(`${typing.username} is typing...`);
    });
  }

  subscribeToNotifications() {
    this.client.subscribe('/user/queue/notifications', (message) => {
      const notification = JSON.parse(message.body);
      console.log('Notification:', notification);
    });
  }

  sendMessage(conversationId, content) {
    this.client.publish({
      destination: `/app/chat.send/${conversationId}`,
      body: JSON.stringify({ content })
    });
  }

  sendTypingNotification(conversationId) {
    this.client.publish({
      destination: `/app/chat.typing/${conversationId}`,
      body: ''
    });
  }

  onNewMessage(message) {
    console.log(`${message.senderUsername}: ${message.content}`);
    // Update UI
  }

  disconnect() {
    this.client.deactivate();
  }
}

// Usage
const chat = new ChatClient(accessToken);
chat.connect();

// Send a message
chat.sendMessage('conv-uuid-1', 'Hello!');

// Typing indicator
chat.sendTypingNotification('conv-uuid-1');
```

---

## Error Handling

### Error Response Format
**Status Code:** 400, 401, 403, 404, 409, 500

**Response:**
```json
{
  "status": "401",
  "error": "UNAUTHORIZED",
  "message": "Invalid email or password",
  "timestamp": 1728316200000
}
```

### Common Error Codes

| Status | Error | Meaning |
|--------|-------|---------|
| 401 | UNAUTHORIZED | Invalid/expired token, wrong credentials |
| 403 | FORBIDDEN | Insufficient permissions or not a member |
| 404 | NOT_FOUND | Resource doesn't exist |
| 409 | CONFLICT | Email already registered |
| 400 | BAD_REQUEST | Invalid input or validation error |
| 500 | INTERNAL_SERVER_ERROR | Server error |

---

## Setup Instructions

### Backend Setup

1. **Clone and navigate:**
   ```bash
   cd chat-love-in
   ```

2. **Update application.properties:**
   ```properties
   # Database
   spring.datasource.url=jdbc:postgresql://localhost:5432/chatinlove
   spring.datasource.username=postgres
   spring.datasource.password=postgres
   
   # JWT
   app.jwt.secret=your-secret-key-min-32-chars
   app.jwt.expiration-minutes=15
   app.jwt.refresh-expiration-days=7
   ```

3. **Build:**
   ```bash
   mvn clean package
   ```

4. **Run:**
   ```bash
   mvn spring-boot:run
   ```

Server runs on `http://localhost:8080`
WebSocket endpoint: `ws://localhost:8080/ws`

### Frontend Setup (React/Angular/Vue)

1. **Install WebSocket client:**
   ```bash
   npm install @stomp/stompjs uuid
   ```

2. **Create API client:**
   ```javascript
   const API_BASE = 'http://localhost:8080/api';
   const WS_URL = 'ws://localhost:8080/ws';
   ```

3. **Implement authentication interceptor** to attach Bearer token to all requests

4. **Connect to WebSocket** on app initialization

5. **Follow the examples above** for sending/receiving messages

---

## Rate Limiting & Constraints

- **Message rate limit:** 100 messages per minute per user
- **Typing notifications:** Debounce to every 1-2 seconds
- **Page size:** Max 100 items per page
- **Conversation search:** Max 1000 conversations per query
- **Token expiration:** 15 minutes (access), 7 days (refresh)

---

## Support & Troubleshooting

### WebSocket Connection Issues
- Ensure JWT token is valid and not expired
- Check CORS settings if frontend is on different origin
- Verify `/ws` endpoint is accessible

### Message Not Sending
- Verify user is active member of conversation
- Check token hasn't expired
- Ensure conversation ID is valid

### Real-time Updates Not Working
- Verify WebSocket connection established (check browser DevTools)
- Ensure subscription paths are correct
- Check message format matches specification

---

**Last Updated:** October 7, 2026
**Backend Version:** 1.0.0
