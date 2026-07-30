import { Navigate } from 'react-router'
import { useSession } from '../../application/auth/use-session'
import { Button } from '../design-system/Button'

export interface DemoAccount {
  username: string
  role: string
}

interface LoginPageProps {
  /** Demo deployments list their shared, public-by-design accounts here. */
  demoAccounts?: DemoAccount[]
  demoPassword?: string
}

/** Entry point of the OAuth2 Authorization Code + PKCE flow (SECURITY.md §2.1). */
export function LoginPage({ demoAccounts = [], demoPassword }: LoginPageProps = {}) {
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
      {demoAccounts.length > 0 && (
        <section
          aria-label="Demo accounts"
          className="w-full rounded-xl border border-surface-line bg-slate-900/60 px-4 py-3 text-sm"
        >
          <h2 className="font-semibold text-brand-100">Demo accounts</h2>
          <p className="mt-1 text-xs text-slate-400">
            This is a public demo — pick a role and explore. Content resets nightly.
          </p>
          <ul className="mt-2 space-y-1">
            {demoAccounts.map((account) => (
              <li key={account.username} className="flex justify-between gap-4">
                <code className="text-slate-200">{account.username}</code>
                <span className="text-slate-400">{account.role}</span>
              </li>
            ))}
          </ul>
          {demoPassword !== undefined && (
            <p className="mt-2 text-xs text-slate-400">
              Password for all accounts: <code className="text-slate-200">{demoPassword}</code>
            </p>
          )}
        </section>
      )}
      <p className="text-center text-xs text-slate-500">
        You will be redirected to the CodePill identity provider. Credentials never touch this app.
      </p>
    </div>
  )
}
