import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/test-utils'
import { aPage, aPill, stubPillApi } from '../../test/fixtures'
import { FeedPage } from './FeedPage'

describe('FeedPage (mobile-first vertical scroll)', () => {
  it('renders the published pills as a vertical feed', async () => {
    const api = stubPillApi({
      listPills: vi
        .fn()
        .mockResolvedValue(aPage([aPill({ id: 'p1' }), aPill({ id: 'p2', title: 'Second pill' })])),
    })

    renderWithProviders(<FeedPage />, { api })

    expect(await screen.findByText('Second pill')).toBeInTheDocument()
    expect(screen.getByRole('feed')).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(2)
  })

  it('loads the next page on demand until the feed is exhausted', async () => {
    const listPills = vi
      .fn()
      .mockResolvedValueOnce(aPage([aPill({ id: 'p1' })], 0, 2))
      .mockResolvedValueOnce(aPage([aPill({ id: 'p2', title: 'Second pill' })], 1, 2))
    const api = stubPillApi({ listPills })

    renderWithProviders(<FeedPage />, { api })

    await userEvent.click(await screen.findByRole('button', { name: /load more/i }))

    expect(await screen.findByText('Second pill')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /load more/i })).not.toBeInTheDocument(),
    )
  })

  it('auto-loads the next page when the sentinel enters the viewport', async () => {
    const observed: {
      callback: IntersectionObserverCallback | null
      options: IntersectionObserverInit | undefined
    } = { callback: null, options: undefined }
    class FakeIntersectionObserver {
      constructor(callback: IntersectionObserverCallback, options?: IntersectionObserverInit) {
        observed.callback = callback
        observed.options = options
      }
      observe() {}
      disconnect() {}
    }
    vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver)
    try {
      const listPills = vi
        .fn()
        .mockResolvedValueOnce(aPage([aPill({ id: 'p1' })], 0, 2))
        .mockResolvedValueOnce(aPage([aPill({ id: 'p2', title: 'Auto-loaded pill' })], 1, 2))
      const api = stubPillApi({ listPills })

      renderWithProviders(<FeedPage />, { api })
      await screen.findByRole('feed')

      // Look-ahead: start loading before the sentinel is actually visible.
      expect(observed.options?.rootMargin).toBe('200px')

      observed.callback?.(
        [{ isIntersecting: true } as IntersectionObserverEntry],
        null as unknown as IntersectionObserver,
      )

      expect(await screen.findByText('Auto-loaded pill')).toBeInTheDocument()
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('shows an empty state when nothing is published yet', async () => {
    const api = stubPillApi({ listPills: vi.fn().mockResolvedValue(aPage([])) })

    renderWithProviders(<FeedPage />, { api })

    expect(await screen.findByText(/no pills published yet/i)).toBeInTheDocument()
  })

  it('shows an error state with retry when the API fails', async () => {
    const listPills = vi
      .fn()
      .mockRejectedValueOnce(new Error('down'))
      .mockResolvedValueOnce(aPage([aPill({ title: 'Recovered pill' })]))
    const api = stubPillApi({ listPills })

    renderWithProviders(<FeedPage />, { api })

    await userEvent.click(await screen.findByRole('button', { name: /try again/i }))

    expect(await screen.findByText('Recovered pill')).toBeInTheDocument()
  })
})
