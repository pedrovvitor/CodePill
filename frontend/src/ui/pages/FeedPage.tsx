import { useCallback, useEffect, useRef } from 'react'
import { usePillFeed } from '../../application/pills/use-pill-feed'
import { PillCard } from '../components/PillCard'
import { Button } from '../design-system/Button'
import { Spinner } from '../design-system/Spinner'

/** Auto-loads the next page when the end of the feed scrolls into view. */
function FeedSentinel({ onVisible }: { onVisible: () => void }) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (typeof IntersectionObserver === 'undefined' || ref.current === null) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) onVisible()
      },
      // Look-ahead: start fetching before the sentinel actually scrolls into view.
      { rootMargin: '200px' },
    )
    observer.observe(ref.current)
    return () => observer.disconnect()
  }, [onVisible])

  return <div ref={ref} aria-hidden className="h-px" />
}

/** Vertical, mobile-first infinite feed of published pills (newest first). */
export function FeedPage() {
  const feed = usePillFeed()
  const pills = feed.data?.pages.flatMap((page) => page.content) ?? []

  // Stable across renders (fetchNextPage is referentially stable in TanStack
  // Query v5) so the sentinel's observer is not torn down every render.
  const { fetchNextPage } = feed
  const loadNextPage = useCallback(() => void fetchNextPage(), [fetchNextPage])

  if (feed.isPending) {
    return (
      <div className="flex justify-center py-16">
        <Spinner label="Loading the feed" />
      </div>
    )
  }

  if (feed.isError) {
    return (
      <div className="flex flex-col items-center gap-4 py-16 text-center">
        <p className="text-slate-300">The feed is unavailable right now.</p>
        <Button variant="ghost" onClick={() => void feed.refetch()}>
          Try again
        </Button>
      </div>
    )
  }

  if (pills.length === 0) {
    return (
      <p className="py-16 text-center text-slate-400">No pills published yet — check back soon.</p>
    )
  }

  return (
    <div role="feed" aria-busy={feed.isFetchingNextPage} className="flex snap-y flex-col gap-4">
      {pills.map((pill) => (
        <PillCard key={pill.id} pill={pill} />
      ))}

      {feed.hasNextPage && (
        <>
          <FeedSentinel onVisible={loadNextPage} />
          <Button
            variant="ghost"
            disabled={feed.isFetchingNextPage}
            onClick={() => void feed.fetchNextPage()}
          >
            {feed.isFetchingNextPage ? 'Loading…' : 'Load more'}
          </Button>
        </>
      )}
    </div>
  )
}
