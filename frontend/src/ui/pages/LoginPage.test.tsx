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

function renderAt(route: string, loginPage = <LoginPage />) {
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<p>the feed</p>} />
      <Route path="/login" element={loginPage} />
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

  describe('demo credentials panel (public by design in demo deployments)', () => {
    const demoAccounts = [
      { username: 'demo-learner', role: 'Learner' },
      { username: 'demo-author', role: 'Author' },
      { username: 'demo-curator', role: 'Curator' },
    ]

    it('lists the demo accounts and shared password when provided', () => {
      useSessionMock.mockReturnValue(aSession({ isAuthenticated: false }))
      renderAt('/login', <LoginPage demoAccounts={demoAccounts} demoPassword="codepill-demo" />)

      const panel = screen.getByRole('region', { name: /demo accounts/i })
      expect(panel).toHaveTextContent('demo-learner')
      expect(panel).toHaveTextContent('demo-author')
      expect(panel).toHaveTextContent('demo-curator')
      expect(panel).toHaveTextContent('codepill-demo')
    })

    it('renders nothing demo-related by default', () => {
      useSessionMock.mockReturnValue(aSession({ isAuthenticated: false }))
      renderAt('/login')
      expect(screen.queryByRole('region', { name: /demo accounts/i })).not.toBeInTheDocument()
    })
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
