import { describe, expect, it } from 'vitest'
import { safeReturnPath } from './return-path'

describe('safeReturnPath', () => {
  it('preserves a shared pill path across sign-in', () => {
    expect(safeReturnPath('/pills/e6f1a1c0-0000-4000-8000-000000000001')).toBe(
      '/pills/e6f1a1c0-0000-4000-8000-000000000001',
    )
  })
  it.each([
    undefined,
    null,
    {},
    '//evil.test',
    'https://evil.test',
    '/\\evil.test',
    '/auth/callback',
    '/pills/new',
  ])('rejects unsupported return locations: %s', (value) => {
    expect(safeReturnPath(value)).toBe('/')
  })
})
