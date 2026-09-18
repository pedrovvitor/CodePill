import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { useSession } from '../../application/auth/use-session'
import { ApiError } from '../../application/ports/pill-api-port'
import { pillKeys } from '../../application/pills/query-keys'
import { aPill, aSession, stubPillApi } from '../../test/fixtures'
import { createTestQueryClient, renderWithProviders } from '../../test/test-utils'
import { PillDetailPage } from './PillDetailPage'

vi.mock('../../application/auth/use-session', () => ({ useSession: vi.fn() }))
const pill = aPill({ content: { body: '<script>alert(1)</script>\nFull lesson text.' } })

function show(api = stubPillApi({ getPill: vi.fn().mockResolvedValue(pill) })) {
  const queryClient = createTestQueryClient()
  renderWithProviders(
    <Routes>
      <Route path="/pills/:id" element={<PillDetailPage />} />
    </Routes>,
    { api, queryClient, route: `/pills/${pill.id}` },
  )
  return { api, queryClient }
}

beforeEach(() => vi.mocked(useSession).mockReturnValue(aSession()))

describe('PillDetailPage', () => {
  it('loads the complete body as escaped text and links back to the feed', async () => {
    const { api } = show()
    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: pill.title })).toBeInTheDocument()
    expect(screen.getByText(/<script>alert\(1\)<\/script>/)).toBeInTheDocument()
    expect(document.querySelector('script')).toBeNull()
    expect(api.getPill).toHaveBeenCalledWith(pill.id, expect.any(AbortSignal))
    expect(screen.getByRole('link', { name: 'Back to feed' })).toHaveAttribute('href', '/')
    expect(screen.queryByRole('button', { name: 'Publish pill' })).not.toBeInTheDocument()
  })

  it('shows all structured content without evaluating embedded markup', async () => {
    show(
      stubPillApi({
        getPill: vi.fn().mockResolvedValue(
          aPill({
            content: {
              blocks: [
                { kind: 'markdown', body: '# A full lesson' },
                { kind: 'quiz', answer: 42 },
              ],
            },
          }),
        ),
      }),
    )
    expect(await screen.findByText('# A full lesson')).toBeInTheDocument()
    expect(screen.getByText(/"answer": 42/)).toBeInTheDocument()
  })

  it.each(['DRAFT', 'IN_REVIEW'] as const)(
    'curator publishes a %s and updates the cached detail/feed',
    async (status) => {
      vi.mocked(useSession).mockReturnValue(aSession({ roles: ['CURATOR'] }))
      const { api, queryClient } = show(
        stubPillApi({ getPill: vi.fn().mockResolvedValue(aPill({ status })) }),
      )
      queryClient.setQueryData(pillKeys.feed(), { existing: true })
      await userEvent.click(await screen.findByRole('button', { name: 'Publish pill' }))
      expect(await screen.findByText('Published and available in the feed.')).toBeInTheDocument()
      expect(api.publishPill).toHaveBeenCalledWith(pill.id)
      expect(queryClient.getQueryData(pillKeys.detail(pill.id))).toEqual(aPill())
      expect(queryClient.getQueryState(pillKeys.feed())?.isInvalidated).toBe(true)
      expect(screen.queryByRole('button', { name: 'Publish pill' })).not.toBeInTheDocument()
    },
  )

  it('does not offer publication to an author without curator role', async () => {
    vi.mocked(useSession).mockReturnValue(aSession({ roles: ['AUTHOR'] }))
    show(stubPillApi({ getPill: vi.fn().mockResolvedValue(aPill({ status: 'DRAFT' })) }))
    await screen.findByRole('heading', { name: pill.title })
    expect(screen.queryByRole('button', { name: 'Publish pill' })).not.toBeInTheDocument()
  })

  it('keeps the draft visible and offers retry when publication fails', async () => {
    vi.mocked(useSession).mockReturnValue(aSession({ roles: ['CURATOR'] }))
    const publish = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(403, null))
      .mockResolvedValue(aPill())
    show(
      stubPillApi({
        getPill: vi.fn().mockResolvedValue(aPill({ status: 'DRAFT' })),
        publishPill: publish,
      }),
    )
    await userEvent.click(await screen.findByRole('button', { name: 'Publish pill' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Publication failed')
    await userEvent.click(screen.getByRole('button', { name: 'Publish pill' }))
    expect(await screen.findByText('Published and available in the feed.')).toBeInTheDocument()
  })

  it('shows the same not-found response for hidden or missing content', async () => {
    show(stubPillApi({ getPill: vi.fn().mockRejectedValue(new ApiError(404, null)) }))
    expect(
      await screen.findByText('This pill is unavailable or you do not have access.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })

  it('recovers from a transient detail request failure', async () => {
    show(
      stubPillApi({
        getPill: vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue(pill),
      }),
    )
    await userEvent.click(await screen.findByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: pill.title })).toBeInTheDocument()
  })
})
