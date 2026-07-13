import { describe, expect, it } from 'vitest'
import { buildOidcProviderProps } from './oidc-config'

const config = {
  authority: 'http://localhost:8180/realms/codepill',
  clientId: 'codepill-web',
  origin: 'http://localhost:5173',
}

describe('buildOidcProviderProps (SECURITY.md §2 — Auth Code + PKCE, tokens in memory)', () => {
  it('targets the Keycloak realm with the SPA client', () => {
    const props = buildOidcProviderProps(config)
    expect(props.authority).toBe('http://localhost:8180/realms/codepill')
    expect(props.client_id).toBe('codepill-web')
    expect(props.redirect_uri).toBe('http://localhost:5173/auth/callback')
    expect(props.post_logout_redirect_uri).toBe('http://localhost:5173')
  })

  it('uses the code flow with default scopes (codepill-api is a default client scope)', () => {
    const props = buildOidcProviderProps(config)
    expect(props.response_type).toBe('code')
    expect(props.scope).toBe('openid profile email')
  })

  it('renews silently before expiry', () => {
    const props = buildOidcProviderProps(config)
    expect(props.automaticSilentRenew).toBe(true)
  })

  it('stores tokens in memory — never in localStorage or sessionStorage', async () => {
    const props = buildOidcProviderProps(config)
    expect(props.userStore).toBeDefined()
    await props.userStore?.set('probe', '{"access_token":"secret"}')

    expect(window.localStorage.length).toBe(0)
    expect(window.sessionStorage.length).toBe(0)
    await expect(props.userStore?.get('probe')).resolves.toBe('{"access_token":"secret"}')
  })

  it('keeps PKCE state in sessionStorage — survives the IdP redirect, never localStorage', async () => {
    // NOT in memory: the code flow does a full-page redirect to the IdP and back,
    // which wipes the JS heap — in-memory state would break every login.
    const props = buildOidcProviderProps(config)
    expect(props.stateStore).toBeDefined()
    try {
      await props.stateStore?.set('state-probe', '{"code_verifier":"secret"}')

      expect(window.localStorage.length).toBe(0)
      expect(window.sessionStorage.length).toBe(1)
      await expect(props.stateStore?.get('state-probe')).resolves.toBe('{"code_verifier":"secret"}')
    } finally {
      await props.stateStore?.remove('state-probe')
    }
  })
})
