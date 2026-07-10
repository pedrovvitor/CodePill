import { useQuery } from '@tanstack/react-query'
import { pillKeys } from './query-keys'
import { usePillApi } from './use-pill-api'

export function usePill(id: string) {
  const api = usePillApi()
  return useQuery({
    queryKey: pillKeys.detail(id),
    queryFn: ({ signal }) => api.getPill(id, signal),
    staleTime: 60_000,
  })
}
