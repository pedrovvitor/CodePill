import { z } from 'zod'
import type { PillContent, PillInput } from './pill'

/** Same pattern the OpenAPI contract enforces server-side. */
export const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

/** Parses raw text into a pill content document; only JSON *objects* qualify. */
export function parsePillContent(raw: string): PillContent | null {
  try {
    const value: unknown = JSON.parse(raw)
    if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
      return value as PillContent
    }
    return null
  } catch {
    return null
  }
}

/**
 * Create Pill form schema — field constraints mirror the OpenAPI `PillInput`
 * schema exactly (title ≤160, slug ≤180 + pattern, summary ≤500,
 * duration 1s–24h). Duration is captured in minutes for authoring UX.
 */
export const createPillFormSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, 'Title is required')
    .max(160, 'Title must be at most 160 characters'),
  slug: z
    .string()
    .min(1, 'Slug is required')
    .max(180, 'Slug must be at most 180 characters')
    .regex(SLUG_PATTERN, 'Use lowercase letters, digits and single hyphens (e.g. my-first-pill)'),
  summary: z.string().trim().max(500, 'Summary must be at most 500 characters').optional(),
  type: z.enum(['ARTICLE', 'QUIZ', 'FLASHCARD', 'VIDEO'], 'Pick a pill type'),
  estimatedMinutes: z
    .number('Estimated minutes is required')
    .int('Whole minutes only')
    .min(1, 'At least 1 minute')
    .max(1440, 'At most 24 hours (1440 minutes)'),
  contentJson: z
    .string()
    .refine((raw) => parsePillContent(raw) !== null, 'Content must be a valid JSON object'),
})

export type CreatePillFormValues = z.infer<typeof createPillFormSchema>

/** Maps validated form values to the wire-format `PillInput`. */
export function toPillInput(values: CreatePillFormValues): PillInput {
  const summary = values.summary?.trim() ?? ''
  return {
    title: values.title.trim(),
    slug: values.slug,
    summary: summary === '' ? null : summary,
    type: values.type,
    estimatedDurationSeconds: values.estimatedMinutes * 60,
    content: parsePillContent(values.contentJson) ?? {},
  }
}

/** Suggests a contract-valid slug from a pill title. */
export function suggestSlug(title: string): string {
  const slug = title
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '') // strip combining diacritics left by NFKD
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 180)
  return slug.replace(/-+$/, '')
}
