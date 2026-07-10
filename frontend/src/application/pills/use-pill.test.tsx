import { describe, expect, it, vi } from 'vitest'
import { waitFor } from '@testing-library/react'
import { renderHookWithProviders } from '../../test/test-utils'
import { aPill, stubPillApi } from '../../test/fixtures'
import { usePill } from './use-pill'

describe('usePill', () => {
  it('fetches a single pill by id through the port', async () => {
    const api = stubPillApi({ getPill: vi.fn().mockResolvedValue(aPill({ id: 'p-42' })) })

    const { result } = renderHookWithProviders(() => usePill('p-42'), { api })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.getPill).toHaveBeenCalledWith('p-42', expect.any(AbortSignal))
    expect(result.current.data?.id).toBe('p-42')
  })
})
