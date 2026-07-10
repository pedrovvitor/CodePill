import { describe, expect, it } from 'vitest'
import { hasRole, ROLES } from './roles'

describe('hasRole (role hierarchy — UX gating only, SECURITY.md §3.2.4)', () => {
  it('grants the exact role', () => {
    expect(hasRole(['AUTHOR'], 'AUTHOR')).toBe(true)
  })

  it('grants lower roles to higher ones (ADMIN > CURATOR > AUTHOR > LEARNER)', () => {
    expect(hasRole(['ADMIN'], 'LEARNER')).toBe(true)
    expect(hasRole(['CURATOR'], 'AUTHOR')).toBe(true)
    expect(hasRole(['AUTHOR'], 'LEARNER')).toBe(true)
  })

  it('denies higher roles to lower ones', () => {
    expect(hasRole(['LEARNER'], 'AUTHOR')).toBe(false)
    expect(hasRole(['AUTHOR'], 'CURATOR')).toBe(false)
    expect(hasRole(['CURATOR'], 'ADMIN')).toBe(false)
  })

  it('denies when no roles are granted', () => {
    expect(hasRole([], 'LEARNER')).toBe(false)
  })

  it('ignores unknown role strings from the token', () => {
    expect(hasRole(['offline_access', 'uma_authorization'], 'LEARNER')).toBe(false)
    expect(hasRole(['offline_access', 'AUTHOR'], 'LEARNER')).toBe(true)
  })

  it('exposes the four platform roles in ascending order', () => {
    expect(ROLES).toEqual(['LEARNER', 'AUTHOR', 'CURATOR', 'ADMIN'])
  })
})
