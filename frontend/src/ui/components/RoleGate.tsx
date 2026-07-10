import type { ReactNode } from 'react'
import { hasRole, type Role } from '../../domain/auth/roles'
import { useSession } from '../../application/auth/use-session'

interface RoleGateProps {
  role: Role
  children: ReactNode
  fallback?: ReactNode
}

/** Hides UI the member cannot use. UX only — the backend is the authority. */
export function RoleGate({ role, children, fallback = null }: RoleGateProps) {
  const { roles } = useSession()
  return <>{hasRole(roles, role) ? children : fallback}</>
}
