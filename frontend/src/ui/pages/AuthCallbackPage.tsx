import { Link, Navigate } from 'react-router'
import { useSession } from '../../application/auth/use-session'
import { Spinner } from '../design-system/Spinner'

/** Redirect URI target: shows progress while the code/PKCE exchange completes. */
export function AuthCallbackPage() {
  const session = useSession()

  if (session.error !== null) {
    return (
      <div className="mx-auto flex min-h-dvh w-full max-w-xl flex-col items-center justify-center gap-4 px-6 text-center">
        <p role="alert" className="text-red-300">
          Sign-in failed: {session.error.message}
        </p>
        <Link to="/login" className="font-medium text-brand-100 underline">
          Back to login
        </Link>
      </div>
    )
  }

  if (session.isAuthenticated) return <Navigate to="/" replace />

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3">
      <Spinner label="Signing you in" />
      <p className="text-sm text-slate-400">Signing you in…</p>
    </div>
  )
}
