import { useMutation, useQueryClient } from '@tanstack/react-query'
import { traceUseCase } from '../tracing'
import { pillKeys } from './query-keys'
import { usePillApi } from './use-pill-api'

/** Existing secured publish use case, exposed to the browser with trace correlation. */
export function usePublishPill() {
  const api = usePillApi()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      traceUseCase('publish-pill', { 'codepill.pill.id': id }, () => api.publishPill(id)),
    onSuccess: (pill) => {
      queryClient.setQueryData(pillKeys.detail(pill.id), pill)
      void queryClient.invalidateQueries({ queryKey: pillKeys.feed() })
    },
  })
}
