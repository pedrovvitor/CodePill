import { useMemo, type ReactNode } from 'react'
import { AuthProvider, useAuth } from 'react-oidc-context'
import { QueryClientProvider } from '@tanstack/react-query'
import { PillApiContext } from '../application/pills/pill-api-context'
import { createHttpPillApi } from '../infrastructure/api/http-pill-api'
import { buildOidcProviderProps } from '../infrastructure/auth/oidc-config'
import { appConfig } from '../infrastructure/config/env'
import { buildQueryClient } from './query-client'

const oidcProps = buildOidcProviderProps({
  authority: appConfig.oidcAuthority,
  clientId: appConfig.oidcClientId,
  origin: window.location.origin,
})

const queryClient = buildQueryClient()

/** Remove code/state from the address bar once the token exchange finished. */
function onSigninCallback(): void {
  window.history.replaceState({}, document.title, window.location.pathname)
}

function PillApiWiring({ children }: { children: ReactNode }) {
  const auth = useAuth()
  // The adapter is rebuilt only when the token rotates (silent renew), so
  // requests always carry the current token without any mutable ref.
  const accessToken = auth.user?.access_token ?? null

  const api = useMemo(
    () =>
      createHttpPillApi({
        baseUrl: appConfig.apiBaseUrl,
        getAccessToken: () => accessToken,
      }),
    [accessToken],
  )

  return <PillApiContext value={api}>{children}</PillApiContext>
}

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <AuthProvider {...oidcProps} onSigninCallback={onSigninCallback}>
      <QueryClientProvider client={queryClient}>
        <PillApiWiring>{children}</PillApiWiring>
      </QueryClientProvider>
    </AuthProvider>
  )
}
