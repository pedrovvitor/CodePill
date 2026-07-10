import type { components } from './api-types.gen'

/**
 * Domain vocabulary for the catalog, aliased from the generated OpenAPI types
 * (ARCHITECTURE.md §4.1.3 — API types are generated, never hand-written).
 * The only refinement: the contract declares `content` as an arbitrary JSON
 * object, which the generator can't express better than an empty record.
 */
export type PillContent = Record<string, unknown>

export type Pill = Omit<components['schemas']['Pill'], 'content'> & { content: PillContent }
export type PillInput = Omit<components['schemas']['PillInput'], 'content'> & {
  content: PillContent
}
export type PillPage = Omit<components['schemas']['PillPage'], 'content'> & { content: Pill[] }
export type PillType = components['schemas']['PillType']
export type PillStatus = components['schemas']['PillStatus']
export type ProblemDetails = components['schemas']['Problem']

export const PILL_TYPES: readonly PillType[] = ['ARTICLE', 'QUIZ', 'FLASHCARD', 'VIDEO']

/** Human label for a pill's estimated duration; partial minutes round up. */
export function formatEstimatedDuration(seconds: number): string {
  if (seconds < 60) return `${seconds} sec`
  const minutes = Math.ceil(seconds / 60)
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`
}
