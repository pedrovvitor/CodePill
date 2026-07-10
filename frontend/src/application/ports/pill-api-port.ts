import type { Pill, PillInput, PillPage, ProblemDetails } from '../../domain/catalog/pill'

/**
 * Port to the codepill-catalog API (ARCHITECTURE.md §4.1.4/5): the application
 * layer depends on this interface; `infrastructure` implements it; UI never
 * calls fetch directly.
 */
export interface PillApiPort {
  listPills(params: { page: number; size: number }, signal?: AbortSignal): Promise<PillPage>
  getPill(id: string, signal?: AbortSignal): Promise<Pill>
  createPill(input: PillInput): Promise<Pill>
  updatePill(id: string, input: PillInput): Promise<Pill>
  deletePill(id: string): Promise<void>
  publishPill(id: string): Promise<Pill>
}

/** Non-2xx API responses, carrying the RFC 9457 problem document when present. */
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetails | null

  constructor(status: number, problem: ProblemDetails | null) {
    super(problem?.title ?? `API request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
