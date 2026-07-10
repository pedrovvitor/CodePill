import { vi } from 'vitest'
import type { Pill, PillPage } from '../domain/catalog/pill'
import type { PillApiPort } from '../application/ports/pill-api-port'
import type { Session } from '../application/auth/use-session'

export function aPill(overrides: Partial<Pill> = {}): Pill {
  return {
    id: 'e6f1a1c0-0000-4000-8000-000000000001',
    authorId: 'e6f1a1c0-0000-4000-8000-000000000002',
    title: 'Virtual Threads in 5 Minutes',
    slug: 'virtual-threads-in-5-minutes',
    summary: 'Loom without the mythology.',
    content: { blocks: [] },
    type: 'ARTICLE',
    status: 'PUBLISHED',
    estimatedDurationSeconds: 300,
    publishedAt: '2026-07-09T12:00:00Z',
    createdAt: '2026-07-09T11:00:00Z',
    updatedAt: '2026-07-09T12:00:00Z',
    version: 1,
    ...overrides,
  }
}

export function aPage(pills: Pill[], page = 0, totalPages = 1): PillPage {
  return {
    content: pills,
    page,
    size: pills.length,
    totalElements: pills.length * totalPages,
    totalPages,
  }
}

/** Vitest-mocked implementation of the pill API port; override per test. */
export function stubPillApi(overrides: Partial<PillApiPort> = {}): PillApiPort {
  return {
    listPills: vi.fn().mockResolvedValue(aPage([aPill()])),
    getPill: vi.fn().mockResolvedValue(aPill()),
    createPill: vi.fn().mockResolvedValue(aPill({ status: 'DRAFT' })),
    updatePill: vi.fn().mockResolvedValue(aPill()),
    deletePill: vi.fn().mockResolvedValue(undefined),
    publishPill: vi.fn().mockResolvedValue(aPill()),
    ...overrides,
  }
}

export function aSession(overrides: Partial<Session> = {}): Session {
  return {
    isLoading: false,
    isAuthenticated: true,
    roles: ['LEARNER'],
    displayName: 'dev-learner',
    error: null,
    signIn: vi.fn(),
    signOut: vi.fn(),
    ...overrides,
  }
}

/** Builds an unsigned JWT with the given payload — for claim-decoding tests only. */
export function fakeJwt(payload: Record<string, unknown>): string {
  const encode = (value: unknown) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'none' })}.${encode(payload)}.sig`
}
