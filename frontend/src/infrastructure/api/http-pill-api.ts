import type { Pill, PillPage, ProblemDetails } from '../../domain/catalog/pill'
import { ApiError, type PillApiPort } from '../../application/ports/pill-api-port'

export interface HttpPillApiDeps {
  /** Empty string for same-origin requests (dev proxy / reverse proxy). */
  baseUrl: string
  /** Reads the current access token from memory; never persisted or logged. */
  getAccessToken: () => string | null
}

interface RequestOptions {
  method?: string
  body?: unknown
  signal?: AbortSignal
}

async function readProblem(response: Response): Promise<ProblemDetails | null> {
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('json')) return null
  try {
    return (await response.json()) as ProblemDetails
  } catch {
    return null
  }
}

/** Fetch-based adapter implementing the application's {@link PillApiPort}. */
export function createHttpPillApi({ baseUrl, getAccessToken }: HttpPillApiDeps): PillApiPort {
  async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const headers = new Headers({ accept: 'application/json' })
    const token = getAccessToken()
    if (token !== null) headers.set('authorization', `Bearer ${token}`)
    if (options.body !== undefined) headers.set('content-type', 'application/json')

    const response = await fetch(`${baseUrl}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: options.signal ?? null,
    })

    if (!response.ok) throw new ApiError(response.status, await readProblem(response))
    if (response.status === 204) return undefined as T
    return (await response.json()) as T
  }

  return {
    listPills: ({ page, size }, signal) =>
      request<PillPage>(`/api/v1/pills?page=${page}&size=${size}`, { signal }),
    getPill: (id, signal) => request<Pill>(`/api/v1/pills/${id}`, { signal }),
    createPill: (input) => request<Pill>('/api/v1/pills', { method: 'POST', body: input }),
    updatePill: (id, input) => request<Pill>(`/api/v1/pills/${id}`, { method: 'PUT', body: input }),
    deletePill: (id) => request<void>(`/api/v1/pills/${id}`, { method: 'DELETE' }),
    publishPill: (id) => request<Pill>(`/api/v1/pills/${id}/publish`, { method: 'POST' }),
  }
}
