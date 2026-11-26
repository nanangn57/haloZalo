/**
 * Run: node --experimental-strip-types clients/core/conversationList.test.mjs
 */
import assert from 'node:assert/strict'
import {
  applyMessageToConversations,
  clearConversationUnread,
  toLastMessage,
} from './conversationList.ts'
import { upsertMessage } from './messageList.ts'

const base = {
  conversationId: 'c1',
  type: 'DIRECT',
  memberIds: ['u1', 'u2'],
  lastSeq: 1,
  createdAt: '2026-01-01T00:00:00Z',
  unreadCount: 2,
  lastMessage: { messageId: 'm0', seq: 1, type: 'TEXT', body: 'old', senderId: 'u2' },
}

const incoming = {
  id: 'm2',
  messageId: 'm2',
  conversationId: 'c1',
  senderId: 'u2',
  type: 'TEXT',
  content: 'forwarded-q',
  seq: 3,
  clientMsgId: 'client-1',
  sentAt: 1_700_000_000,
  status: 'sent',
  forwardedFrom: 'orig',
}

const last = toLastMessage(incoming)
assert.equal(last.body, 'forwarded-q')
assert.equal(last.type, 'TEXT')

const updated = applyMessageToConversations([base], incoming, {
  currentUserId: 'u1',
  activeConversationId: null,
})
assert.equal(updated[0].lastMessage?.body, 'forwarded-q')
assert.equal(updated[0].unreadCount, 3)
assert.equal(updated[0].lastSeq, 3)

const whileOpen = applyMessageToConversations([base], incoming, {
  currentUserId: 'u1',
  activeConversationId: 'c1',
})
assert.equal(whileOpen[0].unreadCount, 0)

const cleared = clearConversationUnread([base], 'c1')
assert.equal(cleared[0].unreadCount, 0)

const fromHttp = {
  ...incoming,
  id: 'm2',
}
const fromWs = {
  ...incoming,
  id: 'm2',
  content: 'forwarded-q',
}
const one = upsertMessage([], fromHttp)
const two = upsertMessage(one, fromWs)
assert.equal(two.length, 1)
assert.equal(two[0].messageId, 'm2')

console.log('conversationList.test.mjs: ok')
