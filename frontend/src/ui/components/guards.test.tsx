import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { renderWithProviders } from '../../test/test-utils'
import { aSession } from '../../test/fixtures'
import { useSession } from '../../application/auth/use-session'
import { ProtectedRoute } from './ProtectedRoute'
import { RoleGate } from './RoleGate'

vi.mock('../../application/auth/use-session', () => ({ useSession: vi.fn() }))
const useSessionMock = vi.mocked(useSession)

beforeEach(() => useSessionMock.mockReset())

function renderProtected(route = '/secret') {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<p>login page</p>} />
      <Route element={<ProtectedRoute />}>
        <Route path="/secret" element={<p>secret content</p>} />
      </Route>
    </Routes>,
    { route },
  )
}

describe('ProtectedRoute', () => {
  it('renders the child route when authenticated', () => {
    useSessionMock.mockReturnValue(aSession())
    renderProtected()
    expect(screen.getByText('secret content')).toBeInTheDocument()
  })

  it('redirects anonymous users to /login', () => {
    useSessionMock.mockReturnValue(aSession({ isAuthenticated: false }))
    renderProtected()
    expect(screen.getByText('login page')).toBeInTheDocument()
    expect(screen.queryByText('secret content')).not.toBeInTheDocument()
  })

  it('shows a pending state while the OIDC client is loading', () => {
    useSessionMock.mockReturnValue(aSession({ isLoading: true, isAuthenticated: false }))
    renderProtected()
    expect(screen.getByRole('status')).toBeInTheDocument()
  })
})

describe('RoleGate (UX-only gating — SECURITY.md §3.2.4)', () => {
  it('renders children when the session covers the role (hierarchy applies)', () => {
    useSessionMock.mockReturnValue(aSession({ roles: ['CURATOR'] }))
    renderWithProviders(<RoleGate role="AUTHOR">author tools</RoleGate>)
    expect(screen.getByText('author tools')).toBeInTheDocument()
  })

  it('renders the fallback when the role is missing', () => {
    useSessionMock.mockReturnValue(aSession({ roles: ['LEARNER'] }))
    renderWithProviders(
      <RoleGate role="AUTHOR" fallback={<p>not allowed</p>}>
        author tools
      </RoleGate>,
    )
    expect(screen.getByText('not allowed')).toBeInTheDocument()
    expect(screen.queryByText('author tools')).not.toBeInTheDocument()
  })
})
