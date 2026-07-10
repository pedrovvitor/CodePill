import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { Pill, PillInput } from '../../domain/catalog/pill'
import { traceUseCase } from '../tracing'
import { pillKeys } from './query-keys'
import { usePillApi } from './use-pill-api'

/** Create-pill use case: traced end-to-end, feed invalidated on success. */
export function useCreatePill() {
  const api = usePillApi()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: PillInput) =>
      traceUseCase('create-pill', { 'codepill.pill.type': input.type }, async (span) => {
        const pill: Pill = await api.createPill(input)
        span.setAttribute('codepill.pill.id', pill.id)
        return pill
      }),
    onSuccess: (pill) => {
      queryClient.setQueryData(pillKeys.detail(pill.id), pill)
      void queryClient.invalidateQueries({ queryKey: pillKeys.feed() })
    },
  })
}
