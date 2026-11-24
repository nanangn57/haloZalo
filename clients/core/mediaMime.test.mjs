/**
 * Smoke test for gallery MIME guessing (Expo/iOS often omit Content-Type).
 * Run: node --experimental-strip-types clients/core/mediaMime.test.mjs
 */
import assert from 'node:assert/strict'
import {
  assertMediaWithinLimit,
  guessMimeType,
  mediaMessageBody,
  resolveMediaUrlWithBase,
} from './mediaMime.ts'

assert.equal(guessMimeType('clip.MOV', 'VIDEO'), 'video/quicktime')
assert.equal(guessMimeType('photo.JPG', 'IMAGE'), 'image/jpeg')
assert.equal(guessMimeType('voice.m4a', 'AUDIO'), 'audio/mp4')
assert.equal(guessMimeType('notes.pdf', 'DOCUMENT'), 'application/pdf')
assert.equal(guessMimeType('noext', 'VIDEO'), 'video/mp4')
assert.equal(guessMimeType('noext', 'IMAGE'), 'image/jpeg')
assert.equal(guessMimeType('clip.3gp', 'VIDEO'), 'video/3gpp')

assert.throws(
  () => assertMediaWithinLimit('VIDEO', 20 * 1024 * 1024 + 1),
  /VIDEO is too large/,
)
assert.throws(
  () => assertMediaWithinLimit('VIDEO', 25 * 1024 * 1024),
  /Max 20MB/,
)
assertMediaWithinLimit('VIDEO', 1024)
assertMediaWithinLimit('VIDEO', 20 * 1024 * 1024)
// Unknown size is a no-op here; uploadMediaApi must resolve size and refuse before POST.
assertMediaWithinLimit('VIDEO', null)
assertMediaWithinLimit('VIDEO', undefined)

assert.equal(
  mediaMessageBody('http://localhost:8080/media/4820fb1a-dbb8-49e9-ac53-1368ee869ca0'),
  '/media/4820fb1a-dbb8-49e9-ac53-1368ee869ca0',
)
assert.equal(
  mediaMessageBody('https://example.com/api/messaging/media/abc-123'),
  '/media/abc-123',
)
assert.equal(mediaMessageBody('/media/abc-123'), '/media/abc-123')
assert.equal(mediaMessageBody('abc-123'), '/media/abc-123')

const lan = 'http://192.168.1.4:8080'
assert.equal(
  resolveMediaUrlWithBase('/media/abc', lan),
  'http://192.168.1.4:8080/media/abc',
)
assert.equal(
  resolveMediaUrlWithBase('http://localhost:8080/media/abc', lan),
  'http://192.168.1.4:8080/media/abc',
)
assert.equal(
  resolveMediaUrlWithBase('https://cdn.example.com/media/abc', lan),
  'https://cdn.example.com/media/abc',
)

console.log('mediaMime.test.mjs PASS')
