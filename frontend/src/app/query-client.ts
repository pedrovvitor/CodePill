import { QueryClient } from '@tanstack/react-query'
import { isApiError } from '../application/ports/pill-api-port'

/**
 * Server-state defaults: background refetch stays on (window focus +
 * reconnect), data is fresh for 30s, and 4xx responses are never retried
 * (they are deterministic — auth/validation/ownership).
 */
export function buildQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: (failureCount, error) => {
          if (isApiError(error) && error.status >= 400 && error.status < 500) return false
          return failureCount < 2
        },
      },
    },
  })
}
