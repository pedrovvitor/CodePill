import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/test-utils'
import { aPill, aSession, stubPillApi } from '../../test/fixtures'
import { useSession } from '../../application/auth/use-session'
import { ApiError } from '../../application/ports/pill-api-port'
import { CreatePillPage } from './CreatePillPage'

vi.mock('../../application/auth/use-session', () => ({ useSession: vi.fn() }))
const useSessionMock = vi.mocked(useSession)

beforeEach(() => {
  useSessionMock.mockReset()
  useSessionMock.mockReturnValue(aSession({ roles: ['LEARNER', 'AUTHOR'] }))
})

async function fillValidForm() {
  await userEvent.type(screen.getByLabelText(/title/i), 'Sealed Interfaces 101')
  await userEvent.clear(screen.getByLabelText(/estimated minutes/i))
  await userEvent.type(screen.getByLabelText(/estimated minutes/i), '4')
  await userEvent.selectOptions(screen.getByLabelText(/type/i), 'FLASHCARD')
  await userEvent.type(screen.getByLabelText(/summary/i), 'Closed hierarchies.')
}

describe('CreatePillPage', () => {
  it('suggests a slug from the title while the slug is untouched', async () => {
    renderWithProviders(<CreatePillPage />)

    await userEvent.type(screen.getByLabelText(/title/i), 'Sealed Interfaces 101')

    expect(screen.getByLabelText(/slug/i)).toHaveValue('sealed-interfaces-101')
  })

  it('submits the mapped PillInput (minutes → seconds, JSON content) and shows success', async () => {
    const createPill = vi.fn().mockResolvedValue(aPill({ title: 'Sealed Interfaces 101' }))
    const api = stubPillApi({ createPill })
    renderWithProviders(<CreatePillPage />, { api })

    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: /create pill/i }))

    await waitFor(() => expect(createPill).toHaveBeenCalledTimes(1))
    expect(createPill).toHaveBeenCalledWith({
      title: 'Sealed Interfaces 101',
      slug: 'sealed-interfaces-101',
      summary: 'Closed hierarchies.',
      type: 'FLASHCARD',
      estimatedDurationSeconds: 240,
      content: { blocks: [] },
    })
    expect(await screen.findByText(/pill created/i)).toBeInTheDocument()
  })

  it('blocks submission on schema violations and reports them on the fields', async () => {
    const api = stubPillApi()
    renderWithProviders(<CreatePillPage />, { api })

    await fillValidForm()
    const slug = screen.getByLabelText(/slug/i)
    await userEvent.clear(slug)
    await userEvent.type(slug, 'Not A Slug!')
    await userEvent.click(screen.getByRole('button', { name: /create pill/i }))

    expect(await screen.findByText(/lowercase letters, digits/i)).toBeInTheDocument()
    expect(api.createPill).not.toHaveBeenCalled()
  })

  it('rejects content that is not a JSON object', async () => {
    const api = stubPillApi()
    renderWithProviders(<CreatePillPage />, { api })

    await fillValidForm()
    const content = screen.getByLabelText(/content/i)
    await userEvent.clear(content)
    await userEvent.type(content, 'not json')
    await userEvent.click(screen.getByRole('button', { name: /create pill/i }))

    expect(await screen.findByText(/valid JSON object/i)).toBeInTheDocument()
    expect(api.createPill).not.toHaveBeenCalled()
  })

  it('maps a 409 slug conflict onto the slug field', async () => {
    const conflict = new ApiError(409, { title: 'Slug already in use', status: 409 })
    const api = stubPillApi({ createPill: vi.fn().mockRejectedValue(conflict) })
    renderWithProviders(<CreatePillPage />, { api })

    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: /create pill/i }))

    expect(await screen.findByText(/slug already in use/i)).toBeInTheDocument()
  })

  it('shows a generic problem banner for other API failures', async () => {
    const failure = new ApiError(500, { title: 'Something broke', status: 500 })
    const api = stubPillApi({ createPill: vi.fn().mockRejectedValue(failure) })
    renderWithProviders(<CreatePillPage />, { api })

    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: /create pill/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/something broke/i)
  })

  it('maps 400 field-level problem errors onto the matching form fields', async () => {
    const badRequest = new ApiError(400, {
      title: 'Validation failed',
      status: 400,
      errors: { title: 'Title is blacklisted', estimatedDurationSeconds: 'Too long' },
    })
    const api = stubPillApi({ createPill: vi.fn().mockRejectedValue(badRequest) })
    renderWithProviders(<CreatePillPage />, { api })

    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: /create pill/i }))

    expect(await screen.findByText('Title is blacklisted')).toBeInTheDocument()
    expect(screen.getByText('Too long')).toBeInTheDocument()
  })

  it('shows a generic banner for unexpected non-API failures', async () => {
    const api = stubPillApi({ createPill: vi.fn().mockRejectedValue(new Error('network down')) })
    renderWithProviders(<CreatePillPage />, { api })

    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: /create pill/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/please try again/i)
  })

  it('lets the author create another pill after success', async () => {
    const api = stubPillApi({ createPill: vi.fn().mockResolvedValue(aPill()) })
    renderWithProviders(<CreatePillPage />, { api })

    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: /create pill/i }))
    await userEvent.click(await screen.findByRole('button', { name: /create another/i }))

    expect(screen.getByLabelText(/title/i)).toHaveValue('')
  })

  it('gates the page for members without the AUTHOR role (UX only)', () => {
    useSessionMock.mockReturnValue(aSession({ roles: ['LEARNER'] }))
    renderWithProviders(<CreatePillPage />)

    expect(screen.getByText(/author role/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /create pill/i })).not.toBeInTheDocument()
  })
})
