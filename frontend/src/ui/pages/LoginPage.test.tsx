import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { renderWithProviders } from '../../test/test-utils'
import { aSession } from '../../test/fixtures'
import { useSession } from '../../application/auth/use-session'
import { LoginPage } from './LoginPage'
import { AuthCallbackPage } from './AuthCallbackPage'

vi.mock('../../application/auth/use-session', () => ({ useSession: vi.fn() }))
const useSessionMock = vi.mocked(useSession)

beforeEach(() => useSessionMock.mockReset())

function renderAt(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<p>the feed</p>} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />
    </Routes>,
    { route },
  )
}

describe('LoginPage', () => {
  it('starts the OIDC redirect flow (Authorization Code + PKCE at the IdP)', async () => {
    const session = aSession({ isAuthenticated: false })
    useSessionMock.mockReturnValue(session)
    renderAt('/login')

    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    expect(session.signIn).toHaveBeenCalledTimes(1)
  })

  it('sends already-authenticated users to the feed', () => {
    useSessionMock.mockReturnValue(aSession())
    renderAt('/login')
    expect(screen.getByText('the feed')).toBeInTheDocument()
  })

  it('shows the auth error when the IdP is unreachable', () => {
    useSessionMock.mockReturnValue(
      aSession({ isAuthenticated: false, error: new Error('IdP unreachable') }),
    )
    renderAt('/login')
    expect(screen.getByText(/IdP unreachable/)).toBeInTheDocument()
  })
})

describe('AuthCallbackPage', () => {
  it('shows progress while the code exchange runs', () => {
    useSessionMock.mockReturnValue(aSession({ isLoading: true, isAuthenticated: false }))
    renderAt('/auth/callback')
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('lands on the feed once signed in', () => {
    useSessionMock.mockReturnValue(aSession())
    renderAt('/auth/callback')
    expect(screen.getByText('the feed')).toBeInTheDocument()
  })

  it('offers a way back to login when the exchange fails', () => {
    useSessionMock.mockReturnValue(
      aSession({ isAuthenticated: false, error: new Error('invalid state') }),
    )
    renderAt('/auth/callback')
    expect(screen.getByText(/sign-in failed/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /back to login/i })).toHaveAttribute('href', '/login')
  })
})
