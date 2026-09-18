import { expect, test, type Page } from '@playwright/test'
import { runId, signIn, USERS } from './helpers'

async function openSharedPill(page: Page, url: string, username: string) {
  await page.goto(url)
  await page.getByRole('button', { name: /sign in with codepill id/i }).click()
  await page.getByLabel(/username or email/i).fill(username)
  await page.getByLabel('Password', { exact: true }).fill('codepill-local')
  await page.getByRole('button', { name: 'Sign In' }).click()
  await expect(page.getByText(username)).toBeVisible()
  await expect(page).toHaveURL(url)
}

test('author drafts, curator publishes, learner reads the full lesson through the UI', async ({ browser, page }) => {
  const title = `E2E complete lesson ${runId()}`
  const body = 'A full lesson beyond the feed summary. <script>Never execute authored HTML.</script>'
  await signIn(page, USERS.author)
  await page.getByRole('link', { name: 'Create', exact: true }).click()
  await page.getByLabel('Title', { exact: true }).fill(title)
  await page.getByLabel('Summary').fill('A short summary, distinct from the lesson.')
  await page.getByLabel('Content (JSON)').fill(JSON.stringify({ body }))
  await page.getByRole('button', { name: 'Create pill', exact: true }).click()
  await page.getByRole('link', { name: 'View draft' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Publish pill' })).toHaveCount(0)
  const sharedUrl = page.url()

  const curatorContext = await browser.newContext()
  const learnerContext = await browser.newContext()
  try {
    const learner = await learnerContext.newPage()
    await openSharedPill(learner, sharedUrl, USERS.learner)
    await expect(learner.getByText('This pill is unavailable or you do not have access.')).toBeVisible()

    const curator = await curatorContext.newPage()
    await openSharedPill(curator, sharedUrl, USERS.curator)
    await curator.getByRole('button', { name: 'Publish pill', exact: true }).click()
    await expect(curator.getByText('Published and available in the feed.')).toBeVisible()

    await learner.getByRole('link', { name: 'Back to feed', exact: true }).click()
    await learner.getByRole('link', { name: `Read ${title}`, exact: true }).click()
    await expect(learner.getByRole('article', { name: 'Full pill content' })).toHaveText(body)
    await expect(learner.getByRole('button', { name: 'Publish pill' })).toHaveCount(0)
  } finally {
    await curatorContext.close()
    await learnerContext.close()
  }
})
