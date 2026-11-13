# Frontend Implementation Checklist

Use this checklist to ensure your frontend implementation is complete and correct.

---

## 📋 Phase 1: Setup & Authentication (Must Complete First)

### Environment Setup
- [ ] Node.js 16+ installed
- [ ] React/Angular/Vue project created
- [ ] `.env` file created with:
  - [ ] `VITE_API_URL=http://localhost:8080/api`
  - [ ] `VITE_WS_URL=ws://localhost:8080/ws`
- [ ] Dependencies installed: `npm install @stomp/stompjs axios`

### API Client
- [ ] Create `services/apiClient.js`
- [ ] Setup axios instance with base URL
- [ ] Add request interceptor to attach JWT token
- [ ] Add response interceptor to handle 401 errors
- [ ] Implement token refresh on 401
- [ ] Redirect to login on refresh failure

### WebSocket Hook
- [ ] Create `hooks/useWebSocket.js`
- [ ] Accepts `accessToken` as parameter
- [ ] Manages StompClient lifecycle
- [ ] Exposes `publish()` and `subscribe()` methods
- [ ] Handles connection/disconnection events
- [ ] Returns `connected` state

### Authentication Pages
- [ ] Login form with email and password
- [ ] Register form with email, username, password
- [ ] Password validation (min 8 chars)
- [ ] Email validation
- [ ] Store tokens after successful auth:
  - [ ] `localStorage.setItem('accessToken', token)`
  - [ ] `localStorage.setItem('refreshToken', token)`
- [ ] Redirect to chat after login
- [ ] Show loading state during auth
- [ ] Show error messages from server

### Testing
- [ ] Can register new user ✅
- [ ] Can login with registered user ✅
- [ ] Token stored in localStorage ✅
- [ ] Can access `/api/auth/principal` with token ✅
- [ ] 401 error triggers token refresh ✅

---

## 💬 Phase 2: REST Chat Functionality

### Conversation List
- [ ] Page component created (`pages/Chat.jsx`)
- [ ] Fetch conversations on mount:
  - [ ] `GET /api/conversations?page=0&size=20`
  - [ ] Display in list format
  - [ ] Show last message and timestamp
- [ ] Pagination:
  - [ ] "Load more" button works
  - [ ] Loads next page: `?page=1&size=20`
- [ ] Click conversation to select:
  - [ ] Updates selected state
  - [ ] Highlight selected item
  - [ ] Fetches messages for that conversation

### Message History
- [ ] Load messages on conversation select:
  - [ ] `GET /api/conversations/{id}/messages?size=50`
- [ ] Display messages in chronological order
  - [ ] Oldest at top
  - [ ] Newest at bottom
- [ ] Pagination:
  - [ ] "Load earlier messages" button
  - [ ] Use `?before={timestamp}&size=50`
  - [ ] Concatenate to existing messages
- [ ] Message display:
  - [ ] Show sender username
  - [ ] Show message content
  - [ ] Show timestamp
  - [ ] Show "Edited" if `editedAt` is not null
  - [ ] Hide if `deletedAt` is not null
  - [ ] Show read indicator if `isRead` is true

### Send Message
- [ ] Message input field in chat window
- [ ] Submit button or Enter key to send
- [ ] `POST /api/conversations/{id}/messages`
- [ ] Request body: `{ content: "..." }`
- [ ] Clear input after sending
- [ ] Show loading state while sending
- [ ] Display error if sending fails
- [ ] Optimistically display message before server response

### Message Actions
- [ ] Edit message:
  - [ ] Only show edit button for own messages
  - [ ] `PUT /api/conversations/messages/{id}`
  - [ ] Show edit UI (modal or inline)
  - [ ] Request body: `{ content: "..." }`
- [ ] Delete message:
  - [ ] Only show delete button for own messages
  - [ ] Confirm before deleting
  - [ ] `DELETE /api/conversations/messages/{id}`
  - [ ] Remove from UI after deletion
- [ ] Mark as read:
  - [ ] Auto-mark when message appears in viewport
  - [ ] `POST /api/conversations/{id}/messages/{id}/read`

### Private Conversations
- [ ] Start conversation button/UI
- [ ] Search or select user to chat with
- [ ] `POST /api/conversations/private`
- [ ] Request body: `{ recipientId: "..." }`
- [ ] Creates conversation or returns existing one
- [ ] Automatically switch to new conversation
- [ ] Add to conversation list

### Testing
- [ ] Can load and display conversations ✅
- [ ] Can load message history ✅
- [ ] Can send message and see it in REST response ✅
- [ ] Can edit message ✅
- [ ] Can delete message ✅
- [ ] Can create private conversation ✅
- [ ] Can load more messages (pagination) ✅

---

## 🔌 Phase 3: WebSocket Real-time Features

### WebSocket Connection
- [ ] Connect to WebSocket on app load
- [ ] Pass `accessToken` in Authorization header
- [ ] Handle connection established event
- [ ] Handle connection failed event (show error)
- [ ] Handle connection lost event (show reconnecting)
- [ ] Implement reconnection logic with backoff
- [ ] Disconnect on app unload/logout

### Subscribe to Messages
- [ ] When conversation selected, subscribe:
  - [ ] `/topic/conversations/{id}`
  - [ ] Store subscription reference
- [ ] When conversation unselected, unsubscribe
- [ ] When message received:
  - [ ] Parse JSON payload
  - [ ] Add to messages list
  - [ ] Scroll to bottom if at bottom
  - [ ] Don't add if sender is self (already in list from REST)

### Subscribe to Typing
- [ ] When conversation selected, subscribe:
  - [ ] `/topic/conversations/{id}/typing`
- [ ] When typing event received:
  - [ ] Extract `userId` and `username`
  - [ ] Don't show if sender is self
  - [ ] Display "user is typing..." UI
  - [ ] Hide after 3 seconds or new message
- [ ] Debounce sending typing notifications:
  - [ ] Send only every 1-2 seconds
  - [ ] Not on every keystroke
  - [ ] Publish to `/app/chat.typing/{id}`

### Real-time Message Display
- [ ] Messages from WebSocket appear instantly
- [ ] Read indicator updates in real-time
- [ ] Conversation list updates when new message received
- [ ] Move conversation to top (last message)
- [ ] Show unread badge if applicable

### Personal Notifications
- [ ] Subscribe to `/user/queue/notifications`
- [ ] Handle various notification types:
  - [ ] Group invites
  - [ ] Invite responses
  - [ ] User online/offline (future)
  - [ ] Missed calls (future)
- [ ] Display toast/notification UI
- [ ] Allow dismissing notifications

### Testing
- [ ] WebSocket connects after login ✅
- [ ] Can receive messages in real-time ✅
- [ ] Typing indicator appears and disappears ✅
- [ ] Can send message via WebSocket ✅
- [ ] REST and WebSocket messages sync correctly ✅
- [ ] Unsubscribe works when leaving conversation ✅

---

## 🎨 Phase 4: UI/UX Polish

### Layout
- [ ] Responsive design (mobile, tablet, desktop)
- [ ] Conversation list sidebar (300px fixed)
- [ ] Chat window takes remaining space
- [ ] Message input at bottom with send button
- [ ] User info header showing active user

### States & Indicators
- [ ] Loading states:
  - [ ] Messages loading
  - [ ] Conversation loading
  - [ ] Sending message
- [ ] Error states:
  - [ ] Network error
  - [ ] Auth error (401)
  - [ ] Server error (500)
- [ ] Empty states:
  - [ ] No conversations
  - [ ] No messages in conversation
  - [ ] No search results

### User Experience
- [ ] Auto-scroll to latest message
- [ ] Scroll to message on load
- [ ] Smooth animations
- [ ] Timestamp formatting (human-readable)
- [ ] Avatar/profile pictures (optional)
- [ ] User presence indicators (online/offline)
- [ ] Unread message count

### Accessibility
- [ ] Keyboard navigation
- [ ] ARIA labels on buttons
- [ ] Focus indicators
- [ ] Color contrast ratios
- [ ] Screen reader support

---

## 🔒 Phase 5: Security & Best Practices

### Token Management
- [ ] Secure token storage strategy
- [ ] Never log tokens to console
- [ ] Clear tokens on logout
- [ ] Handle token expiry gracefully
- [ ] Refresh token before expiry if possible

### Input Validation
- [ ] Validate email format on register
- [ ] Validate password length (min 8)
- [ ] Trim whitespace from inputs
- [ ] Validate message content (not empty)
- [ ] Sanitize displayed content (prevent XSS)

### Error Handling
- [ ] Catch and display API errors
- [ ] Show user-friendly error messages
- [ ] Log errors for debugging
- [ ] Retry logic for failed requests
- [ ] Timeout handling for slow requests

### Performance
- [ ] Lazy load conversations (pagination)
- [ ] Lazy load messages (cursor pagination)
- [ ] Debounce typing notifications
- [ ] Unsubscribe from WebSocket topics
- [ ] Cleanup on component unmount
- [ ] Memoize expensive computations

### Testing
- [ ] Unit tests for utility functions
- [ ] Integration tests for API calls
- [ ] Component tests for UI
- [ ] E2E tests for full user flows
- [ ] Test error scenarios

---

## 📊 Phase 6: Performance & Monitoring

### Frontend Metrics
- [ ] Track page load time
- [ ] Track API response times
- [ ] Track WebSocket connection time
- [ ] Log errors to monitoring service
- [ ] Setup analytics

### Optimization
- [ ] Code splitting
- [ ] Lazy loading components
- [ ] Image optimization
- [ ] Bundle size monitoring
- [ ] CSS minification
- [ ] Production build

---

## 🚀 Deployment Checklist

### Before Going Live
- [ ] Backend running on production server
- [ ] Database backups configured
- [ ] SSL/TLS certificates installed
- [ ] CORS properly configured (specific origins)
- [ ] JWT secret changed to strong random value
- [ ] API rate limiting enabled
- [ ] Logging configured for production
- [ ] Error monitoring setup (Sentry, etc.)

### Frontend Deployment
- [ ] Environment variables set for production
- [ ] API URL points to production backend
- [ ] WebSocket URL points to production backend
- [ ] Build optimization enabled
- [ ] Source maps stripped in production
- [ ] Service worker configured (optional)
- [ ] CDN setup for static assets

### Post-Deployment
- [ ] Test all endpoints with production backend
- [ ] Monitor error logs
- [ ] Monitor performance metrics
- [ ] Test on mobile devices
- [ ] Test on different browsers
- [ ] Test with slow network (throttle)

---

## 📝 Documentation

- [ ] README for developers
- [ ] Setup instructions
- [ ] Architecture diagram
- [ ] API integration guide
- [ ] Component documentation
- [ ] Environment variables explained
- [ ] Deployment guide

---

## 📞 Common Issues & Solutions

### Issue: WebSocket won't connect
```
✓ Check token is valid
✓ Check Authorization header format: Bearer {token}
✓ Check backend server is running
✓ Check firewall allows WebSocket connections
✓ Check browser DevTools Network tab for details
```

### Issue: 401 on every request
```
✓ Token might be expired, refresh it
✓ localStorage might not have token, check browser storage
✓ Token format might be wrong, should be: Bearer eyJ...
```

### Issue: Real-time messages not appearing
```
✓ Check WebSocket is connected (not closed)
✓ Check subscription destination is correct
✓ Check message payload format matches spec
✓ Check you're not in the SENDING client
✓ Check browser console for errors
```

### Issue: CORS errors
```
✓ Backend should allow all origins: @CrossOrigin("*")
✓ Ensure requests have correct headers
✓ Clear browser cache and cookies
```

---

## ✅ Final Sign-Off

Before marking project complete:

- [ ] All 6 phases completed
- [ ] All tests passing
- [ ] No console errors
- [ ] No console warnings (except unavoidable)
- [ ] Responsive on mobile/tablet/desktop
- [ ] Tested with actual backend
- [ ] Code reviewed by team member
- [ ] Documentation complete
- [ ] Ready for production deployment

---

## 📞 Support Resources

1. **Backend Issues?** Check backend README and logs
2. **API Questions?** See `API_DOCUMENTATION.md`
3. **Protocol Issues?** See `FRONTEND_PROTOCOL.md`
4. **Setup Problems?** See `FRONTEND_QUICK_START.md`
5. **This Checklist?** Check off items as you complete them

---

**Good luck! 🚀**

The backend team has done the hard work.  
Now it's your turn to build an amazing frontend! 💪

Let us know if you have any questions or run into issues!
