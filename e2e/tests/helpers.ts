import { expect, request, type APIRequestContext, type Browser, type Page } from '@playwright/test'

export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080'

/** Dev-realm users imported from ops/keycloak/realm-codepill.json (local/CI only). */
export const USERS = {
  learner: 'dev-learner',
  author: 'dev-author',
  curator: 'dev-curator',
} as const

// Local/CI dev-realm password (documented in STATE_OF_THE_APP.md); not a secret.
const DEV_REALM_PASSWORD = 'codepill-local'

/** Unique-enough suffix so reruns against a persistent local stack never collide. */
export function runId(): string {
  return `${Date.now().toString(36)}${Math.floor(Math.random() * 1e4).toString(36)}`
}

/**
 * Full OAuth2 Authorization Code + PKCE journey: SPA login page → Keycloak
 * hosted login form → redirect back → authenticated shell.
 */
export async function signIn(page: Page, username: string): Promise<void> {
  await page.goto('/')
  // Anonymous visitors are pushed to /login by ProtectedRoute.
  await page.getByRole('button', { name: /sign in with codepill id/i }).click()
  // Keycloak's hosted login form (never rendered by the SPA).
  await page.getByLabel(/username or email/i).fill(username)
  await page.getByLabel('Password', { exact: true }).fill(DEV_REALM_PASSWORD)
  await page.getByRole('button', { name: 'Sign In' }).click()
  // Back in the SPA, authenticated: the shell header shows the username.
  await expect(page.getByText(username)).toBeVisible()
}

/**
 * Signs the user in inside a throwaway context and returns their access token.
 * Tokens live in memory only (SECURITY.md §2.2.3) and do not survive a page
 * reload, so the token is captured from the Authorization header of the feed
 * request the SPA fires right after login — the listener must be registered
 * before the sign-in completes.
 */
export async function tokenFor(browser: Browser, username: string): Promise<string> {
  const context = await browser.newContext()
  const page = await context.newPage()
  try {
    const apiRequest = page.waitForRequest(
      (req) => req.url().includes('/api/v1/pills') && req.headers()['authorization'] !== undefined,
    )
    await signIn(page, username)
    const header = (await apiRequest).headers()['authorization'] ?? ''
    return header.replace(/^Bearer /, '')
  } finally {
    await context.close()
  }
}

export interface SeededPill {
  id: string
  title: string
  slug: string
}

/**
 * API-based fixture (§3.3: each test seeds and owns its data): the author
 * drafts pills, the curator publishes them — same paths the product uses.
 */
export async function seedPublishedPills(
  authorToken: string,
  curatorToken: string,
  count: number,
  seedId: string,
): Promise<SeededPill[]> {
  const api: APIRequestContext = await request.newContext({ baseURL: API_URL })
  const pills: SeededPill[] = []
  try {
    for (let i = 0; i < count; i += 1) {
      const title = `E2E feed pill ${seedId} #${i}`
      const created = await api.post('/api/v1/pills', {
        headers: { authorization: `Bearer ${authorToken}` },
        data: {
          title,
          slug: `e2e-feed-${seedId}-${i}`,
          summary: `Seeded by the feed-scroll journey (${seedId}).`,
          type: 'ARTICLE',
          estimatedDurationSeconds: 120,
          content: { blocks: [{ kind: 'markdown', body: `# ${title}` }] },
        },
      })
      expect(created.status(), `draft pill #${i} should be created`).toBe(201)
      const pill = (await created.json()) as SeededPill
      const published = await api.post(`/api/v1/pills/${pill.id}/publish`, {
        headers: { authorization: `Bearer ${curatorToken}` },
      })
      expect(published.status(), `pill #${i} should be published`).toBe(200)
      pills.push(pill)
    }
    return pills
  } finally {
    await api.dispose()
  }
}
