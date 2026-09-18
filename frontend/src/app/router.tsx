import { lazy, Suspense } from 'react'
import { Link, Route, Routes } from 'react-router'
import { appConfig } from '../infrastructure/config/env'
import { AppShell } from '../ui/components/AppShell'
import { ProtectedRoute } from '../ui/components/ProtectedRoute'
import { Spinner } from '../ui/design-system/Spinner'

// Shared demo credentials, public by design (they match the demo realm import
// in ops/k8s/charts/codepill-infra — keep in sync with demoUserPassword there).
const demoAccounts = appConfig.demoMode
  ? [
      { username: 'demo-learner', role: 'Learner' },
      { username: 'demo-author', role: 'Author' },
      { username: 'demo-curator', role: 'Curator' },
    ]
  : []
const demoPassword = appConfig.demoMode ? 'codepill-demo' : undefined

// Route-level code splitting: each page (and whatever only it pulls in —
// react-hook-form/zod live behind CreatePillPage) loads on first navigation.
const AuthCallbackPage = lazy(() =>
  import('../ui/pages/AuthCallbackPage').then((m) => ({ default: m.AuthCallbackPage })),
)
const CreatePillPage = lazy(() =>
  import('../ui/pages/CreatePillPage').then((m) => ({ default: m.CreatePillPage })),
)
const FeedPage = lazy(() => import('../ui/pages/FeedPage').then((m) => ({ default: m.FeedPage })))
const PillDetailPage = lazy(() =>
  import('../ui/pages/PillDetailPage').then((m) => ({ default: m.PillDetailPage })),
)
const LoginPage = lazy(() =>
  import('../ui/pages/LoginPage').then((m) => ({ default: m.LoginPage })),
)

function NotFoundPage() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3">
      <p className="text-slate-300">This page does not exist.</p>
      <Link to="/" className="font-medium text-brand-100 underline">
        Back to the feed
      </Link>
    </div>
  )
}

function PageFallback() {
  return (
    <div className="flex min-h-dvh items-center justify-center">
      <Spinner label="Loading page" />
    </div>
  )
}

export function AppRoutes() {
  return (
    <Suspense fallback={<PageFallback />}>
      <Routes>
        <Route
          path="/login"
          element={<LoginPage demoAccounts={demoAccounts} demoPassword={demoPassword} />}
        />
        <Route path="/auth/callback" element={<AuthCallbackPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route path="/" element={<FeedPage />} />
            <Route path="/pills/new" element={<CreatePillPage />} />
            <Route path="/pills/:id" element={<PillDetailPage />} />
          </Route>
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  )
}
