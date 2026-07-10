/** Central query-key registry — server state lives in TanStack Query only. */
export const pillKeys = {
  all: ['pills'] as const,
  feed: () => [...pillKeys.all, 'feed'] as const,
  detail: (id: string) => [...pillKeys.all, 'detail', id] as const,
}
