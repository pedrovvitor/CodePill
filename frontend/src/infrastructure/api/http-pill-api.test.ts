import { afterEach, describe, expect, it, vi } from 'vitest'
import { createHttpPillApi } from './http-pill-api'
import { ApiError } from '../../application/ports/pill-api-port'
import type { Pill, PillInput } from '../../domain/catalog/pill'

const pill: Pill = {
  id: 'e6f1a1c0-0000-4000-8000-000000000001',
  authorId: 'e6f1a1c0-0000-4000-8000-000000000002',
  title: 'Virtual Threads',
  slug: 'virtual-threads',
  summary: null,
  content: { blocks: [] },
  type: 'ARTICLE',
  status: 'PUBLISHED',
  estimatedDurationSeconds: 300,
  publishedAt: '2026-07-09T12:00:00Z',
  createdAt: '2026-07-09T11:00:00Z',
  updatedAt: '2026-07-09T12:00:00Z',
  version: 1,
}

const input: PillInput = {
  title: 'Virtual Threads',
  slug: 'virtual-threads',
  summary: null,
  content: { blocks: [] },
  type: 'ARTICLE',
  estimatedDurationSeconds: 300,
}

function jsonResponse(status: number, body: unknown, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': contentType },
  })
}

function apiWithFetch(response: Response, token: string | null = 'test-token') {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  const api = createHttpPillApi({ baseUrl: '', getAccessToken: () => token })
  return { api, fetchMock }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('createHttpPillApi', () => {
  it('GETs the feed page with pagination params and bearer token', async () => {
    const { api, fetchMock } = apiWithFetch(
      jsonResponse(200, { content: [pill], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    )

    const page = await api.listPills({ page: 0, size: 20 })

    expect(page.content[0]?.slug).toBe('virtual-threads')
    const [url, init] = fetchMock.mock.calls[0] ?? []
    expect(String(url)).toBe('/api/v1/pills?page=0&size=20')
    expect(new Headers(init?.headers).get('authorization')).toBe('Bearer test-token')
  })

  it('omits the Authorization header when there is no token', async () => {
    const { api, fetchMock } = apiWithFetch(jsonResponse(200, pill), null)

    await api.getPill(pill.id)

    const [, init] = fetchMock.mock.calls[0] ?? []
    expect(new Headers(init?.headers).has('authorization')).toBe(false)
  })

  it('POSTs a new pill as JSON', async () => {
    const { api, fetchMock } = apiWithFetch(jsonResponse(201, pill))

    const created = await api.createPill(input)

    expect(created.id).toBe(pill.id)
    const [url, init] = fetchMock.mock.calls[0] ?? []
    expect(String(url)).toBe('/api/v1/pills')
    expect(init?.method).toBe('POST')
    expect(new Headers(init?.headers).get('content-type')).toBe('application/json')
    expect(JSON.parse(String(init?.body))).toEqual(input)
  })

  it('PUTs updates to the pill resource', async () => {
    const { api, fetchMock } = apiWithFetch(jsonResponse(200, pill))

    await api.updatePill(pill.id, input)

    const [url, init] = fetchMock.mock.calls[0] ?? []
    expect(String(url)).toBe(`/api/v1/pills/${pill.id}`)
    expect(init?.method).toBe('PUT')
  })

  it('DELETE resolves on 204 with no body', async () => {
    const { api, fetchMock } = apiWithFetch(new Response(null, { status: 204 }))

    await expect(api.deletePill(pill.id)).resolves.toBeUndefined()
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('DELETE')
  })

  it('POSTs publish to the publish sub-resource', async () => {
    const { api, fetchMock } = apiWithFetch(jsonResponse(200, { ...pill, status: 'PUBLISHED' }))

    await api.publishPill(pill.id)

    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(`/api/v1/pills/${pill.id}/publish`)
  })

  it('throws ApiError carrying the RFC 9457 problem document', async () => {
    const problem = { title: 'Slug already in use', status: 409, detail: 'virtual-threads' }
    const { api } = apiWithFetch(jsonResponse(409, problem, 'application/problem+json'))

    const error = await api.createPill(input).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).problem?.title).toBe('Slug already in use')
  })

  it('throws ApiError with null problem when the error body is not problem+json', async () => {
    const { api } = apiWithFetch(new Response('<html>bad gateway</html>', { status: 502 }))

    const error = await api.getPill(pill.id).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(502)
    expect((error as ApiError).problem).toBeNull()
  })

  it('sends every mutation with an abort signal so hung requests time out', async () => {
    const { api, fetchMock } = apiWithFetch(jsonResponse(201, pill))

    await api.createPill(input)

    const signal = fetchMock.mock.calls[0]?.[1]?.signal
    expect(signal).toBeInstanceOf(AbortSignal)
    expect(signal?.aborted).toBe(false)
  })

  it('still honors a caller-provided abort signal alongside the timeout', async () => {
    const { api, fetchMock } = apiWithFetch(jsonResponse(200, pill))
    const controller = new AbortController()

    await api.getPill(pill.id, controller.signal)

    const signal = fetchMock.mock.calls[0]?.[1]?.signal
    expect(signal).toBeInstanceOf(AbortSignal)
    expect(signal?.aborted).toBe(false)

    controller.abort()
    expect(signal?.aborted).toBe(true)
  })

  it('prefixes requests with the configured base URL', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse(200, pill))
    vi.stubGlobal('fetch', fetchMock)
    const api = createHttpPillApi({
      baseUrl: 'https://api.codepill.dev',
      getAccessToken: () => null,
    })

    await api.getPill(pill.id)

    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      `https://api.codepill.dev/api/v1/pills/${pill.id}`,
    )
  })
})
