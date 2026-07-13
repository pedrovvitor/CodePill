import { useInfiniteQuery } from '@tanstack/react-query'
import { pillKeys } from './query-keys'
import { usePillApi } from './use-pill-api'

export const FEED_PAGE_SIZE = 10
/** Cap the cache so an endless scroll session cannot grow memory unbounded. */
export const FEED_MAX_PAGES = 5

/** Infinite vertical feed of published pills, newest first. */
export function usePillFeed() {
  const api = usePillApi()
  return useInfiniteQuery({
    queryKey: pillKeys.feed(),
    queryFn: ({ pageParam, signal }) =>
      api.listPills({ page: pageParam, size: FEED_PAGE_SIZE }, signal),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
    // Required by maxPages: lets the query re-fetch pages trimmed from the front.
    getPreviousPageParam: (firstPage) => (firstPage.page > 0 ? firstPage.page - 1 : undefined),
    maxPages: FEED_MAX_PAGES,
    staleTime: 30_000,
  })
}
