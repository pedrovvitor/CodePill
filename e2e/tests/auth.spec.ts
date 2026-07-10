import { expect, test } from '@playwright/test'
import { signIn, USERS } from './helpers'

test.describe('OAuth2 login (Authorization Code + PKCE via Keycloak)', () => {
  test('learner signs in and reaches the protected feed', async ({ page }) => {
    await signIn(page, USERS.learner)

    // Landed on the shell: brand, primary nav, and the feed route.
    await expect(page.getByText('CodePill', { exact: true })).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Primary' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Feed' })).toBeVisible()
    await expect(
      page.getByRole('feed').or(page.getByText(/no pills published yet/i)),
    ).toBeVisible()

    // Role-gated UX: a plain learner gets no authoring entry point.
    await expect(page.getByRole('link', { name: 'Create' })).toHaveCount(0)
  })

  test('author sees the authoring destination after signing in', async ({ page }) => {
    await signIn(page, USERS.author)

    await expect(page.getByRole('link', { name: 'Create' })).toBeVisible()
  })

  test('sign out ends the session and returns to the login screen', async ({ page }) => {
    await signIn(page, USERS.learner)

    await page.getByRole('button', { name: 'Sign out' }).click()

    // Keycloak end-session redirects back; ProtectedRoute pushes to /login.
    await expect(page.getByRole('button', { name: /sign in with codepill id/i })).toBeVisible()
  })
})
