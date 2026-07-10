import { describe, expect, it } from 'vitest'
import { decodeRolesFromAccessToken } from './token-claims'
import { fakeJwt } from '../../test/fixtures'

describe('decodeRolesFromAccessToken', () => {
  it('extracts the flat roles claim from the access token payload', () => {
    const token = fakeJwt({ sub: 'u-1', roles: ['LEARNER', 'AUTHOR'] })
    expect(decodeRolesFromAccessToken(token)).toEqual(['LEARNER', 'AUTHOR'])
  })

  it('handles base64url payloads (chars that plain atob rejects)', () => {
    const token = fakeJwt({ sub: 'u-1', name: '???~~~>>>', roles: ['CURATOR'] })
    expect(decodeRolesFromAccessToken(token)).toEqual(['CURATOR'])
  })

  it('drops non-string entries from a malformed roles claim', () => {
    const token = fakeJwt({ roles: ['LEARNER', 42, null] })
    expect(decodeRolesFromAccessToken(token)).toEqual(['LEARNER'])
  })

  it.each([
    ['undefined token', undefined],
    ['empty token', ''],
    ['not a JWT', 'just-a-string'],
    ['garbage payload', 'a.%%%%.c'],
    ['payload without roles', fakeJwt({ sub: 'u-1' })],
    ['roles is not an array', fakeJwt({ roles: 'ADMIN' })],
  ])('returns no roles for %s', (_name, token) => {
    expect(decodeRolesFromAccessToken(token)).toEqual([])
  })
})
