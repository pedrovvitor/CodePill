import { expect, test } from '@playwright/test'
import { runId, signIn, USERS } from './helpers'

test.describe('Authoring: create a microlearning pill', () => {
  test('author fills the form and the pill is saved as a draft', async ({ page }) => {
    const id = runId()
    await signIn(page, USERS.author)

    await page.getByRole('link', { name: 'Create' }).click()
    await expect(page.getByRole('heading', { name: 'Create a pill' })).toBeVisible()

    await page.getByLabel('Title').fill(`E2E authored pill ${id}`)
    // Slug is auto-suggested from the title — assert, then keep it.
    await expect(page.getByLabel('Slug')).toHaveValue(`e2e-authored-pill-${id}`)
    await page.getByLabel('Summary').fill('Authored end-to-end by Playwright.')
    await page.getByLabel('Type').selectOption('FLASHCARD')
    await page.getByLabel('Estimated minutes').fill('3')

    await page.getByRole('button', { name: 'Create pill' }).click()

    await expect(page.getByText(/pill created/i)).toBeVisible()
    await expect(page.getByText(`E2E authored pill ${id}`)).toBeVisible()
  })

  test('contract violations are rejected on the client before any API call', async ({ page }) => {
    await signIn(page, USERS.author)
    await page.getByRole('link', { name: 'Create' }).click()

    await page.getByLabel('Title').fill('Broken slug pill')
    const slug = page.getByLabel('Slug')
    await slug.fill('Not A Valid Slug!')
    await page.getByRole('button', { name: 'Create pill' }).click()

    await expect(page.getByText(/lowercase letters, digits/i)).toBeVisible()
  })
})
