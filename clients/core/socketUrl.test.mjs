/**
 * Node smoke test for messaging WebSocket URL join (no Vite needed).
 * Run: node clients/core/socketUrl.test.mjs
 */
import assert from 'node:assert/strict'

function buildMessagingWsUrl(wsBaseUrl, auth) {
  const base = wsBaseUrl.replace(/\/?$/, '/')
  const url = new URL('ws', base)
  if (auth.mode === 'TOKEN_QUERY') {
    url.searchParams.set('access_token', auth.accessToken)
  } else if (auth.mode === 'USER_ID_QUERY') {
    url.searchParams.set('userId', auth.userId)
  }
  return url.toString()
}

const cloud = buildMessagingWsUrl('wss://halozalo.nltuanthanh.io.vn/api/messaging', {
  mode: 'TOKEN_QUERY',
  accessToken: 'tok',
})
assert.equal(
  cloud,
  'wss://halozalo.nltuanthanh.io.vn/api/messaging/ws?access_token=tok',
  'cloud base must keep /api/messaging',
)

const brokenOld = new URL('/ws', 'wss://halozalo.nltuanthanh.io.vn/api/messaging').toString()
assert.equal(brokenOld, 'wss://halozalo.nltuanthanh.io.vn/ws')
assert.notEqual(cloud.split('?')[0], brokenOld)

const local = buildMessagingWsUrl('ws://localhost:8080', {
  mode: 'USER_ID_QUERY',
  userId: 'u1',
})
assert.equal(local, 'ws://localhost:8080/ws?userId=u1')

console.log('socketUrl.test.mjs PASS')
