import { Navigate, Outlet, useLocation } from 'react-router'
import { useSession } from '../../application/auth/use-session'
import { Spinner } from '../design-system/Spinner'

/**
 * Route guard: unauthenticated visitors are sent to /login. UX only — every
 * API call is independently authenticated by the backend (SECURITY.md §3).
 */
export function ProtectedRoute() {
  const session = useSession()
  const location = useLocation()

  if (session.isLoading) {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Spinner label="Checking your session" />
      </div>
    )
  }

  if (!session.isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <Outlet />
}
