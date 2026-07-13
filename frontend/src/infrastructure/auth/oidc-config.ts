import { InMemoryWebStorage, WebStorageStateStore, type UserManagerSettings } from 'oidc-client-ts'

export interface OidcConfigInput {
  authority: string
  clientId: string
  /** window.location.origin — injected for testability. */
  origin: string
}

/**
 * OIDC client settings per SECURITY.md §2: Authorization Code + PKCE (S256 is
 * enforced by the IdP client), silent renew, and tokens held **in memory** —
 * never localStorage/sessionStorage. `codepill-api` is a default client scope
 * on `codepill-web`, so the audience and `roles` claims arrive without being
 * requested explicitly.
 */
export function buildOidcProviderProps({
  authority,
  clientId,
  origin,
}: OidcConfigInput): UserManagerSettings {
  return {
    authority,
    client_id: clientId,
    redirect_uri: `${origin}/auth/callback`,
    post_logout_redirect_uri: origin,
    response_type: 'code',
    scope: 'openid profile email',
    automaticSilentRenew: true,
    userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
    // PKCE handshake state must survive the full-page redirect to the IdP, so it
    // cannot live in memory; sessionStorage is tab-scoped and cleared on close
    stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
  }
}
