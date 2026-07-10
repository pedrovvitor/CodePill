import { Link, Route, Routes } from 'react-router'
import { AppShell } from '../ui/components/AppShell'
import { ProtectedRoute } from '../ui/components/ProtectedRoute'
import { AuthCallbackPage } from '../ui/pages/AuthCallbackPage'
import { CreatePillPage } from '../ui/pages/CreatePillPage'
import { FeedPage } from '../ui/pages/FeedPage'
import { LoginPage } from '../ui/pages/LoginPage'

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

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<FeedPage />} />
          <Route path="/pills/new" element={<CreatePillPage />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
