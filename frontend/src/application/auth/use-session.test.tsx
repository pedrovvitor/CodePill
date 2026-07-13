import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { fakeJwt } from '../../test/fixtures'
import { useSession } from './use-session'

vi.mock('react-oidc-context', () => ({ useAuth: vi.fn() }))

const useAuthMock = vi.mocked(useAuth)

type AuthShape = ReturnType<typeof useAuth>

function authState(overrides: Partial<AuthShape>): AuthShape {
  return {
    isLoading: false,
    isAuthenticated: false,
    user: null,
    error: undefined,
    signinRedirect: vi.fn().mockResolvedValue(undefined),
    signoutRedirect: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  } as AuthShape
}

function renderUseSession(queryClient = new QueryClient()) {
  return renderHook(() => useSession(), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  })
}

beforeEach(() => {
  useAuthMock.mockReset()
})

describe('useSession', () => {
  it('exposes roles decoded from the access token and the display name', () => {
    useAuthMock.mockReturnValue(
      authState({
        isAuthenticated: true,
        user: {
          access_token: fakeJwt({ roles: ['LEARNER', 'AUTHOR'] }),
          profile: { preferred_username: 'dev-author' },
        } as never,
      }),
    )

    const { result } = renderUseSession()

    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.roles).toEqual(['LEARNER', 'AUTHOR'])
    expect(result.current.displayName).toBe('dev-author')
  })

  it('is anonymous with no roles when signed out', () => {
    useAuthMock.mockReturnValue(authState({}))

    const { result } = renderUseSession()

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.roles).toEqual([])
    expect(result.current.displayName).toBeNull()
  })

  it('delegates signIn/signOut to the OIDC redirect flows', () => {
    const auth = authState({})
    useAuthMock.mockReturnValue(auth)

    const { result } = renderUseSession()
    result.current.signIn()
    result.current.signOut()

    expect(auth.signinRedirect).toHaveBeenCalledTimes(1)
    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
  })

  it('clears the query cache on signOut so no server state leaks to the next user', () => {
    const auth = authState({})
    useAuthMock.mockReturnValue(auth)
    const queryClient = new QueryClient()
    queryClient.setQueryData(['pills', 'feed'], { content: ['user-a-data'] })

    const { result } = renderUseSession(queryClient)
    result.current.signOut()

    expect(queryClient.getQueryData(['pills', 'feed'])).toBeUndefined()
    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
  })

  it('surfaces the auth error', () => {
    const error = new Error('IdP unreachable')
    useAuthMock.mockReturnValue(authState({ error: error as never }))

    const { result } = renderUseSession()

    expect(result.current.error).toBe(error)
  })
})
