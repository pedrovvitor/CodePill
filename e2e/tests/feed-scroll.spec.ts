import { expect, test } from '@playwright/test'
import { runId, seedPublishedPills, signIn, tokenFor, USERS } from './helpers'

const PAGE_SIZE = 10 // FEED_PAGE_SIZE in the SPA

test.describe('Feed: vertical scroll with automatic paging', () => {
  test('learner scrolls the feed and the next page loads automatically', async ({
    page,
    browser,
  }) => {
    const id = runId()
    // Seed through the product's own API paths: author drafts, curator publishes.
    const [authorToken, curatorToken] = await Promise.all([
      tokenFor(browser, USERS.author),
      tokenFor(browser, USERS.curator),
    ])
    await seedPublishedPills(authorToken, curatorToken, PAGE_SIZE + 2, id)

    await signIn(page, USERS.learner)
    await expect(page.getByRole('feed')).toBeVisible()

    // Newest-first: the seeded pills fill the whole first page.
    const articles = page.getByRole('article')
    await expect(articles).toHaveCount(PAGE_SIZE)
    await expect(page.getByText(`E2E feed pill ${id} #${PAGE_SIZE + 1}`)).toBeVisible()

    // Scroll to the end of the list — the sentinel auto-loads page two.
    await page.getByRole('button', { name: /load more/i }).scrollIntoViewIfNeeded()

    await expect
      .poll(async () => articles.count(), { message: 'next feed page should auto-load' })
      .toBeGreaterThan(PAGE_SIZE)
    // The oldest seeded pill lived on page two and is now rendered.
    await expect(page.getByText(`E2E feed pill ${id} #0`)).toBeVisible()
  })
})
