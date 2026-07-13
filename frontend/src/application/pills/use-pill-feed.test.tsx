import { describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { renderHookWithProviders } from '../../test/test-utils'
import { aPage, aPill, stubPillApi } from '../../test/fixtures'
import { FEED_MAX_PAGES, FEED_PAGE_SIZE, usePillFeed } from './use-pill-feed'
import { usePillApi } from './use-pill-api'

describe('usePillFeed', () => {
  it('loads the first feed page through the port', async () => {
    const api = stubPillApi({
      listPills: vi.fn().mockResolvedValue(aPage([aPill({ title: 'Page one pill' })], 0, 1)),
    })

    const { result } = renderHookWithProviders(() => usePillFeed(), { api })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.listPills).toHaveBeenCalledWith(
      { page: 0, size: FEED_PAGE_SIZE },
      expect.any(AbortSignal),
    )
    expect(result.current.data?.pages[0]?.content[0]?.title).toBe('Page one pill')
    expect(result.current.hasNextPage).toBe(false)
  })

  it('pages forward while the server reports more pages', async () => {
    const listPills = vi
      .fn()
      .mockResolvedValueOnce(aPage([aPill({ id: 'p1' })], 0, 2))
      .mockResolvedValueOnce(aPage([aPill({ id: 'p2' })], 1, 2))
    const api = stubPillApi({ listPills })

    const { result } = renderHookWithProviders(() => usePillFeed(), { api })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.hasNextPage).toBe(true)

    await result.current.fetchNextPage()

    await waitFor(() => expect(result.current.data?.pages).toHaveLength(2))
    expect(listPills).toHaveBeenLastCalledWith(
      { page: 1, size: FEED_PAGE_SIZE },
      expect.any(AbortSignal),
    )
    expect(result.current.hasNextPage).toBe(false)
  })

  it(`keeps at most ${FEED_MAX_PAGES} pages in the cache and can page back to trimmed ones`, async () => {
    const listPills = vi
      .fn()
      .mockImplementation(({ page }: { page: number }) =>
        Promise.resolve(aPage([aPill({ id: `p${page}` })], page, FEED_MAX_PAGES + 2)),
      )
    const api = stubPillApi({ listPills })

    const { result } = renderHookWithProviders(() => usePillFeed(), { api })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    // Load one page past the cap: 1 initial + 5 next = 6 fetched, 5 retained.
    for (let i = 0; i < FEED_MAX_PAGES; i += 1) {
      await result.current.fetchNextPage()
      await waitFor(() => expect(result.current.isFetchingNextPage).toBe(false))
    }

    expect(result.current.data?.pages).toHaveLength(FEED_MAX_PAGES)
    // The oldest page (0) was trimmed, so the window starts at page 1 …
    expect(result.current.data?.pages[0]?.page).toBe(1)
    // … and remains reachable by paging backwards.
    expect(result.current.hasPreviousPage).toBe(true)
  })

  it('surfaces port failures as query errors', async () => {
    const api = stubPillApi({ listPills: vi.fn().mockRejectedValue(new Error('boom')) })

    const { result } = renderHookWithProviders(() => usePillFeed(), { api })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('usePillApi', () => {
  it('fails fast when rendered outside of a PillApiContext (wiring bug)', () => {
    const silence = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => renderHook(() => usePillApi())).toThrow(/PillApiContext/)
    silence.mockRestore()
  })
})
