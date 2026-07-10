import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { renderWithProviders } from '../../test/test-utils'
import { aSession } from '../../test/fixtures'
import { useSession } from '../../application/auth/use-session'
import { AppShell } from './AppShell'

vi.mock('../../application/auth/use-session', () => ({ useSession: vi.fn() }))
const useSessionMock = vi.mocked(useSession)

beforeEach(() => useSessionMock.mockReset())

function renderShell(roles: string[]) {
  useSessionMock.mockReturnValue(aSession({ roles, displayName: 'dev-user' }))
  return renderWithProviders(
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<p>feed content</p>} />
      </Route>
    </Routes>,
  )
}

describe('AppShell', () => {
  it('renders brand, user name and the routed page', () => {
    renderShell(['LEARNER'])
    expect(screen.getByText('CodePill')).toBeInTheDocument()
    expect(screen.getByText('dev-user')).toBeInTheDocument()
    expect(screen.getByText('feed content')).toBeInTheDocument()
  })

  it('hides the Create destination from plain learners', () => {
    renderShell(['LEARNER'])
    expect(screen.getByRole('link', { name: /feed/i })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /create/i })).not.toBeInTheDocument()
  })

  it('offers the Create destination to authors', () => {
    renderShell(['LEARNER', 'AUTHOR'])
    expect(screen.getByRole('link', { name: /create/i })).toHaveAttribute('href', '/pills/new')
  })

  it('signs out through the OIDC flow', async () => {
    const session = aSession({ roles: ['LEARNER'] })
    useSessionMock.mockReturnValue(session)
    renderWithProviders(
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<p>feed content</p>} />
        </Route>
      </Routes>,
    )

    await userEvent.click(screen.getByRole('button', { name: /sign out/i }))

    expect(session.signOut).toHaveBeenCalledTimes(1)
  })
})
