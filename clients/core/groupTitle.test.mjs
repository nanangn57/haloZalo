/**
 * Run: node --experimental-strip-types clients/core/groupTitle.test.mjs
 */
import assert from 'node:assert/strict'
import {
  formatMemberGroupTitle,
  resolveGroupDisplayTitle,
} from './groupTitle.ts'

assert.equal(formatMemberGroupTitle(['A', 'B']), 'A, B')
assert.equal(formatMemberGroupTitle(['A', 'B', 'C', 'D']), 'A, B, C…')
assert.equal(formatMemberGroupTitle([]), '')

assert.equal(
  resolveGroupDisplayTitle({
    customName: 'Family',
    memberNames: ['A', 'B'],
  }),
  'Family',
)
assert.equal(
  resolveGroupDisplayTitle({
    customName: '  ',
    memberNames: ['Ngoc Anh', 'huy', 'beebee', 'other'],
  }),
  'Ngoc Anh, huy, beebee…',
)
assert.equal(
  resolveGroupDisplayTitle({ customName: null, memberNames: [] }),
  'Group',
)

console.log('groupTitle.test.mjs: ok')
