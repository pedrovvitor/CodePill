import { useMemo } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { decodeRolesFromAccessToken } from './token-claims'

export interface Session {
  isLoading: boolean
  isAuthenticated: boolean
  /** Roles from the access token — UX gating only; the backend decides. */
  roles: string[]
  displayName: string | null
  error: Error | null
  signIn: () => void
  signOut: () => void
}

/** The app's single view on authentication state (client state, not server state). */
export function useSession(): Session {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const accessToken = auth.user?.access_token
  const roles = useMemo(() => decodeRolesFromAccessToken(accessToken), [accessToken])

  return {
    isLoading: auth.isLoading,
    isAuthenticated: auth.isAuthenticated,
    roles,
    displayName: auth.user?.profile.preferred_username ?? null,
    error: auth.error ?? null,
    signIn: () => void auth.signinRedirect(),
    signOut: () => {
      // Server-state cache is per-user: purge it before the redirect so
      // nothing cached for user A survives into the next session.
      queryClient.clear()
      void auth.signoutRedirect()
    },
  }
}
