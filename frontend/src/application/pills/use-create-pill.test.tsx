import { describe, expect, it, vi } from 'vitest'
import { waitFor } from '@testing-library/react'
import { createTestQueryClient, renderHookWithProviders } from '../../test/test-utils'
import { aPill, stubPillApi } from '../../test/fixtures'
import { ApiError } from '../ports/pill-api-port'
import { pillKeys } from './query-keys'
import { useCreatePill } from './use-create-pill'
import type { PillInput } from '../../domain/catalog/pill'

const input: PillInput = {
  title: 'Records vs Lombok',
  slug: 'records-vs-lombok',
  summary: null,
  content: { blocks: [] },
  type: 'ARTICLE',
  estimatedDurationSeconds: 240,
}

describe('useCreatePill', () => {
  it('creates through the port, primes the detail cache and refreshes the feed', async () => {
    const created = aPill({ id: 'new-pill-id', status: 'DRAFT', slug: input.slug })
    const api = stubPillApi({ createPill: vi.fn().mockResolvedValue(created) })
    const queryClient = createTestQueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHookWithProviders(() => useCreatePill(), { api, queryClient })
    result.current.mutate(input)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.createPill).toHaveBeenCalledWith(input)
    expect(queryClient.getQueryData(pillKeys.detail('new-pill-id'))).toEqual(created)
    expect(invalidate).toHaveBeenCalledWith({ queryKey: pillKeys.feed() })
  })

  it('exposes API failures (e.g. 409 slug conflict) to the caller', async () => {
    const conflict = new ApiError(409, { title: 'Slug already in use', status: 409 })
    const api = stubPillApi({ createPill: vi.fn().mockRejectedValue(conflict) })

    const { result } = renderHookWithProviders(() => useCreatePill(), { api })
    result.current.mutate(input)

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBe(conflict)
  })
})
