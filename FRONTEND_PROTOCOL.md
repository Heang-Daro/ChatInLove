# Frontend Protocol Contract - chatInLove

This document defines the exact protocol and message formats that the frontend should use to communicate with the backend.

## Quick Reference

**Backend Server:** `http://localhost:8080`
**WebSocket:** `ws://localhost:8080/ws`
**API Base:** `/api`

---

## Authentication Token Management

### How Tokens Work

1. **Access Token**
   - Short-lived (15 minutes)
   - Used in `Authorization: Bearer {token}` header
   - Includes session ID (`sid`) claim

2. **Refresh Token**
   - Long-lived (7 days)
   - Stored securely (httpOnly cookie or secure localStorage)
   - Only used to get new access tokens

### Token Usage Pattern

```
┌─────────────────────────────────────────────┐
│ 1. User Registers or Logs In                │
│    POST /api/auth/register or /api/auth/login
│    Response: { accessToken, refreshToken }  │
└──────────────────────┬──────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────┐
│ 2. Store Tokens Securely                    │
│    - accessToken: Can use sessionStorage    │
│    - refreshToken: Must be secure           │
└──────────────────────┬──────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────┐
│ 3. Every HTTP Request                       │
│    Authorization: Bearer {accessToken}      │
│    Content-Type: application/json           │
└──────────────────────┬──────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────┐
│ 4. Token Expires?                           │
│    - Response: 401 Unauthorized             │
│    - POST /api/auth/refresh                 │
│    - Get new accessToken                    │
│    - Retry request                          │
└─────────────────────────────────────────────┘
```

---

## REST API Contract

### Standard Request Headers
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

### Standard Response Format

**Success (200, 201, 204):**
- 2xx status code
- JSON body (or empty for 204)

**Error (4xx, 5xx):**
```json
{
  "status": "401",
  "error": "UNAUTHORIZED",
  "message": "Invalid email or password",
  "timestamp": 1728316200000
}
```

### Key Endpoints Summary

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login user |
| POST | `/api/auth/refresh` | Refresh access token |
| GET | `/api/auth/principal` | Get current user info |
| POST | `/api/auth/logout` | Logout current session |
| POST | `/api/auth/logout-all` | Logout all devices |
| GET | `/api/conversations` | Get user's conversations |
| GET | `/api/conversations/{id}` | Get conversation details |
| POST | `/api/conversations/private` | Start private chat |
| GET | `/api/conversations/{id}/messages` | Get messages |
| POST | `/api/conversations/{id}/messages` | Send message |
| PUT | `/api/conversations/messages/{id}` | Edit message |
| DELETE | `/api/conversations/messages/{id}` | Delete message |
| POST | `/api/conversations/{id}/messages/{id}/read` | Mark read |

---

## WebSocket/STOMP Protocol

### Connection Lifecycle

```
1. Create STOMP Client
   ↓
2. Set Connection Headers (Authorization)
   ↓
3. Connect to ws://localhost:8080/ws
   ↓
4. Server validates JWT in CONNECT frame
   ↓
5. Subscribe to destinations
   ↓
6. Publish to destinations
   ↓
7. Receive broadcasts on subscribed topics
   ↓
8. Disconnect (graceful or timeout)
```

### STOMP Frame Format

**CONNECT:**
```
CONNECT
Authorization:Bearer {accessToken}
accept-version:1.0,1.1,1.2
heart-beat:10000,10000

^@
```

**SUBSCRIBE:**
```
SUBSCRIBE
id:sub-1
destination:/topic/conversations/conv-uuid-1

^@
```

**SEND:**
```
SEND
destination:/app/chat.send/conv-uuid-1
content-length:30

{"content":"Hello!"}^@
```

---

## Message Flow Diagrams

### Sending a Message

```
Frontend                                Backend
   │                                       │
   ├──── SEND /app/chat.send/conv-1 ─────>│
   │     { content: "Hello!" }             │
   │                                       │
   │                                   1. Validate user is member
   │                                   2. Save to database
   │                                   3. Broadcast to all subscribers
   │                                       │
   │  <──── MESSAGE /topic/conv-1 ────────┤
   │       { id, senderId, content, ... }  │
   │                                       │
   └─── Display in UI                      │
```

### Private Conversation Creation

```
Frontend                                Backend
   │                                       │
   ├──── POST /api/conversations/private ─>│
   │     { recipientId: "user-2" }        │
   │                                       │
   │                                   1. Check if exists
   │                                   2. Create if needed
   │                                   3. Add both users
   │                                       │
   │  <──── 201 Conversation Object ──────┤
   │       { id, type, members }          │
   │                                       │
   └─── Open chat with this user          │
```

### Typing Indicator

```
Frontend                                Backend
   │                                       │
   ├──── SEND /app/chat.typing/conv-1 ───>│
   │     (empty payload)                   │
   │                                   1. Get user info
   │                                   2. Broadcast typing event
   │                                       │
   │  <──── MESSAGE /topic/conv-1/typing ─┤
   │       { userId, username, isTyping }  │
   │                                       │
   └─── Show "john_doe is typing..." UI    │
```

### Incoming Message (Real-time)

```
Backend                                Frontend (Receiver)
   │                                       │
   ├──── MESSAGE /topic/conv-1 ───────────>│
   │     { id, senderId, content, ... }    │
   │                                       │
   │                                   1. Check not sender
   │                                   2. Show in UI
   │                                   3. Optional: Auto-mark read
   │                                       │
```

---

## Subscription Management

### What to Subscribe To

```javascript
// On WebSocket connect:

// 1. For each conversation user is member of
client.subscribe(`/topic/conversations/{conversationId}`, handler);
client.subscribe(`/topic/conversations/{conversationId}/typing`, handler);

// 2. Personal notifications (invites, etc)
client.subscribe('/user/queue/notifications', handler);

// Future: For calls
client.subscribe('/user/queue/calls', handler);
```

### Recommended Subscription Pattern

```javascript
class ConversationSubscriber {
  constructor(client) {
    this.client = client;
    this.subscriptions = new Map();
  }

  subscribeToConversation(conversationId) {
    // Avoid duplicate subscriptions
    if (this.subscriptions.has(conversationId)) {
      return;
    }

    const sub1 = this.client.subscribe(
      `/topic/conversations/${conversationId}`,
      (msg) => this.onNewMessage(msg)
    );

    const sub2 = this.client.subscribe(
      `/topic/conversations/${conversationId}/typing`,
      (msg) => this.onTyping(msg)
    );

    this.subscriptions.set(conversationId, [sub1, sub2]);
  }

  unsubscribeFromConversation(conversationId) {
    const subs = this.subscriptions.get(conversationId);
    if (subs) {
      subs.forEach(sub => sub.unsubscribe());
      this.subscriptions.delete(conversationId);
    }
  }

  onNewMessage(message) {
    const msg = JSON.parse(message.body);
    // Update UI
  }

  onTyping(message) {
    const typing = JSON.parse(message.body);
    // Show typing indicator
  }
}
```

---

## Payload Specifications

### Message Object (from server)

```typescript
interface MessageResponse {
  id: string;                    // UUID
  conversationId: string;        // UUID
  senderId: string;              // User UUID
  senderUsername: string;        // e.g., "john_doe"
  type: "TEXT" | "IMAGE" | "SYSTEM" | "CALL";
  content: string;               // Message text
  isRead: boolean;               // Has recipient read this?
  createdAt: string;             // ISO 8601 datetime
  editedAt: string | null;       // ISO 8601 or null
  deletedAt: string | null;      // ISO 8601 or null (soft delete)
  relatedCallId: string | null;  // For CALL type messages
}
```

### Send Message Payload (to server)

```typescript
interface SendMessageRequest {
  content: string;  // Required, max 10000 chars
}
```

### Conversation Object (from server)

```typescript
interface ConversationResponse {
  id: string;
  type: "PRIVATE" | "GROUP";
  name: string | null;           // Only for GROUP
  description: string | null;    // Only for GROUP
  createdAt: string;             // ISO 8601
  lastMessageAt: string | null;  // ISO 8601 of last message
  members: ConversationMember[];
}

interface ConversationMember {
  userId: string;
  username: string;
  role: "OWNER" | "ADMIN" | "MEMBER";
  joinedAt: string;              // ISO 8601
  leftAt: string | null;         // If left conversation
}
```

### Typing Notification (broadcast)

```typescript
interface TypingNotification {
  userId: string;
  username: string;
  conversationId: string;
  isTyping: boolean;
}
```

### Auth Response

```typescript
interface AuthResponse {
  userId: string;
  accessToken: string;
  refreshToken: string;
  expiresIn: number;            // Seconds
  tokenType: "Bearer";
}
```

---

## Frontend Checklist

### Pre-Implementation
- [ ] Read entire API documentation
- [ ] Setup environment variables for API_URL and WS_URL
- [ ] Install @stomp/stompjs library
- [ ] Plan token storage strategy (sessionStorage vs secure storage)

### Authentication UI
- [ ] Register form (email, username, password validation)
- [ ] Login form (email, password)
- [ ] "Remember me" / persist login
- [ ] Auto-refresh token before expiry
- [ ] Handle 401 errors and re-authenticate

### Chat UI - REST
- [ ] List conversations with pagination
- [ ] Load more conversations (infinite scroll)
- [ ] Load previous messages (cursor pagination)
- [ ] "No more messages" indicator
- [ ] Create private conversation with user search

### Chat UI - WebSocket
- [ ] Connect to WebSocket on app load
- [ ] Maintain subscription map for open conversations
- [ ] Real-time message display
- [ ] Typing indicator (with debounce)
- [ ] Show user online/offline status
- [ ] Handle connection loss and reconnection

### Message Features
- [ ] Send message (REST POST)
- [ ] Edit message (REST PUT)
- [ ] Delete message (REST DELETE)
- [ ] Real-time message receive (WebSocket)
- [ ] Mark message as read
- [ ] Show "Edited" label if edited
- [ ] Hide deleted messages

### Error Handling
- [ ] Display error messages from API
- [ ] Handle expired tokens
- [ ] Handle connection timeouts
- [ ] Show loading states
- [ ] Retry failed requests

### Performance
- [ ] Debounce typing notifications (500ms)
- [ ] Lazy load message history
- [ ] Unsubscribe from conversations when leaving
- [ ] Disconnect WebSocket on logout
- [ ] Cleanup subscriptions on component unmount

---

## Example: React Implementation

```jsx
import { useEffect, useState } from 'react';
import { useStompClient } from './hooks/useStompClient';
import { useApi } from './hooks/useApi';

function ChatApp() {
  const [accessToken, setAccessToken] = useState(null);
  const [conversations, setConversations] = useState([]);
  const [selectedConversation, setSelectedConversation] = useState(null);
  const [messages, setMessages] = useState([]);

  const { connected, publish, subscribe } = useStompClient(accessToken);
  const { fetchConversations, fetchMessages, sendMessage } = useApi(accessToken);

  // On app load
  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (token) setAccessToken(token);
  }, []);

  // Load conversations
  useEffect(() => {
    if (accessToken && connected) {
      loadConversations();
    }
  }, [accessToken, connected]);

  // Subscribe to selected conversation
  useEffect(() => {
    if (!selectedConversation) return;

    const unsubMessages = subscribe(
      `/topic/conversations/${selectedConversation.id}`,
      (msg) => {
        const newMessage = JSON.parse(msg.body);
        setMessages(prev => [newMessage, ...prev]);
      }
    );

    const unsubTyping = subscribe(
      `/topic/conversations/${selectedConversation.id}/typing`,
      (msg) => {
        const typing = JSON.parse(msg.body);
        // Show typing indicator
      }
    );

    return () => {
      unsubMessages?.unsubscribe();
      unsubTyping?.unsubscribe();
    };
  }, [selectedConversation]);

  const loadConversations = async () => {
    const data = await fetchConversations({ page: 0, size: 20 });
    setConversations(data.content);
  };

  const handleSelectConversation = async (conversation) => {
    setSelectedConversation(conversation);
    const data = await fetchMessages(conversation.id, { size: 50 });
    setMessages(data.content);
  };

  const handleSendMessage = (content) => {
    publish(`/app/chat.send/${selectedConversation.id}`, { content });
  };

  const handleTyping = () => {
    publish(`/app/chat.typing/${selectedConversation.id}`, {});
  };

  return (
    <div>
      <ConversationList
        conversations={conversations}
        onSelect={handleSelectConversation}
      />
      <ChatWindow
        conversation={selectedConversation}
        messages={messages}
        onSendMessage={handleSendMessage}
        onTyping={handleTyping}
      />
    </div>
  );
}

export default ChatApp;
```

---

## Common Issues & Solutions

### Issue: WebSocket connection fails
**Solution:** 
- Check token is valid
- Verify Authorization header format: `Bearer {token}`
- Check server is running on correct port

### Issue: Messages not real-time
**Solution:**
- Verify subscription destination is correct
- Check WebSocket connection is active
- Ensure you're subscribed before sending

### Issue: Token expired
**Solution:**
- Call `POST /api/auth/refresh`
- Update stored token
- Retry original request
- Or redirect to login

### Issue: CORS errors
**Solution:**
- Server allows all origins: `@CrossOrigin("*")`
- Ensure requests have proper headers

---

**Version:** 1.0.0  
**Last Updated:** October 7, 2026
