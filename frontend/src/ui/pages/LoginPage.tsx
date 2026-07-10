import { Navigate } from 'react-router'
import { useSession } from '../../application/auth/use-session'
import { Button } from '../design-system/Button'

/** Entry point of the OAuth2 Authorization Code + PKCE flow (SECURITY.md §2.1). */
export function LoginPage() {
  const session = useSession()

  if (session.isAuthenticated) return <Navigate to="/" replace />

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-xl flex-col items-center justify-center gap-8 px-6">
      <div className="text-center">
        <h1 className="text-4xl font-bold text-brand-100">CodePill</h1>
        <p className="mt-2 text-slate-400">Learning in small doses.</p>
      </div>
      {session.error !== null && (
        <p
          role="alert"
          className="rounded-xl border border-red-400/40 bg-red-950/40 px-4 py-3 text-sm text-red-300"
        >
          {session.error.message}
        </p>
      )}
      <Button onClick={session.signIn} className="w-full">
        Sign in with CodePill ID
      </Button>
      <p className="text-center text-xs text-slate-500">
        You will be redirected to the CodePill identity provider. Credentials never touch this app.
      </p>
    </div>
  )
}
