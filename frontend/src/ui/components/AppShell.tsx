import { NavLink, Outlet } from 'react-router'
import { useSession } from '../../application/auth/use-session'
import { RoleGate } from './RoleGate'

function navClasses({ isActive }: { isActive: boolean }): string {
  return `flex min-h-12 flex-1 items-center justify-center rounded-xl text-sm font-medium transition-colors ${
    isActive ? 'bg-brand-600/20 text-brand-100' : 'text-slate-400 hover:text-slate-200'
  }`
}

/**
 * Mobile-first layout: sticky top bar, scrollable vertical content, thumb-reach
 * bottom navigation (fixed, safe-area aware).
 */
export function AppShell() {
  const session = useSession()

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-xl flex-col">
      <header className="sticky top-0 z-10 flex items-center justify-between border-b border-surface-line bg-surface/90 px-4 py-3 backdrop-blur">
        <span className="text-lg font-bold text-brand-100">CodePill</span>
        <div className="flex items-center gap-3">
          {session.displayName !== null && (
            <span className="text-sm text-slate-400">{session.displayName}</span>
          )}
          <button
            type="button"
            onClick={session.signOut}
            className="text-sm font-medium text-slate-300 hover:text-white"
          >
            Sign out
          </button>
        </div>
      </header>

      <main className="flex-1 px-4 pt-4 pb-24">
        <Outlet />
      </main>

      <nav
        aria-label="Primary"
        className="fixed inset-x-0 bottom-0 z-10 mx-auto flex w-full max-w-xl gap-2 border-t border-surface-line bg-surface/95 p-2 pb-[calc(0.5rem+env(safe-area-inset-bottom))] backdrop-blur"
      >
        <NavLink to="/" end className={navClasses}>
          Feed
        </NavLink>
        <RoleGate role="AUTHOR">
          <NavLink to="/pills/new" className={navClasses}>
            Create
          </NavLink>
        </RoleGate>
      </nav>
    </div>
  )
}
