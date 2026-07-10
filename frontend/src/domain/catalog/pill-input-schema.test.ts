import { describe, expect, it } from 'vitest'
import {
  createPillFormSchema,
  parsePillContent,
  suggestSlug,
  toPillInput,
  type CreatePillFormValues,
} from './pill-input-schema'

const validForm: CreatePillFormValues = {
  title: 'Virtual Threads in 5 Minutes',
  slug: 'virtual-threads-in-5-minutes',
  summary: 'Loom without the mythology.',
  type: 'ARTICLE',
  estimatedMinutes: 5,
  contentJson: '{"blocks":[{"kind":"markdown","body":"# Loom"}]}',
}

function errorsOf(values: unknown): Record<string, string[]> {
  const result = createPillFormSchema.safeParse(values)
  if (result.success) return {}
  const out: Record<string, string[]> = {}
  for (const issue of result.error.issues) {
    const key = String(issue.path[0] ?? '_')
    out[key] = [...(out[key] ?? []), issue.message]
  }
  return out
}

describe('createPillFormSchema (mirrors OpenAPI PillInput constraints)', () => {
  it('accepts a valid form', () => {
    expect(createPillFormSchema.safeParse(validForm).success).toBe(true)
  })

  it.each([
    ['empty title', { title: '' }, 'title'],
    ['blank title', { title: '   ' }, 'title'],
    ['title over 160 chars', { title: 'x'.repeat(161) }, 'title'],
    ['empty slug', { slug: '' }, 'slug'],
    ['uppercase slug', { slug: 'Virtual-Threads' }, 'slug'],
    ['slug with spaces', { slug: 'virtual threads' }, 'slug'],
    ['slug with leading hyphen', { slug: '-virtual' }, 'slug'],
    ['slug over 180 chars', { slug: 'a'.repeat(181) }, 'slug'],
    ['summary over 500 chars', { summary: 'x'.repeat(501) }, 'summary'],
    ['zero minutes', { estimatedMinutes: 0 }, 'estimatedMinutes'],
    ['fractional minutes', { estimatedMinutes: 2.5 }, 'estimatedMinutes'],
    ['over 24h', { estimatedMinutes: 1441 }, 'estimatedMinutes'],
    ['NaN minutes (empty number input)', { estimatedMinutes: Number.NaN }, 'estimatedMinutes'],
    ['invalid JSON content', { contentJson: '{nope' }, 'contentJson'],
    ['JSON array content', { contentJson: '[1,2]' }, 'contentJson'],
    ['JSON scalar content', { contentJson: '"text"' }, 'contentJson'],
  ])('rejects %s', (_name, patch, field) => {
    expect(errorsOf({ ...validForm, ...patch })[field]).toBeDefined()
  })

  it('accepts an empty summary (optional field)', () => {
    expect(createPillFormSchema.safeParse({ ...validForm, summary: '' }).success).toBe(true)
  })
})

describe('toPillInput', () => {
  it('converts minutes to seconds and parses content', () => {
    const input = toPillInput(validForm)
    expect(input).toEqual({
      title: 'Virtual Threads in 5 Minutes',
      slug: 'virtual-threads-in-5-minutes',
      summary: 'Loom without the mythology.',
      type: 'ARTICLE',
      estimatedDurationSeconds: 300,
      content: { blocks: [{ kind: 'markdown', body: '# Loom' }] },
    })
  })

  it('maps an empty summary to null (contract: nullable)', () => {
    expect(toPillInput({ ...validForm, summary: '' }).summary).toBeNull()
    expect(toPillInput({ ...validForm, summary: '   ' }).summary).toBeNull()
  })
})

describe('parsePillContent', () => {
  it('parses a JSON object', () => {
    expect(parsePillContent('{"a":1}')).toEqual({ a: 1 })
  })

  it.each([['{oops'], ['[1]'], ['"str"'], ['null'], ['42']])('rejects %s', (raw) => {
    expect(parsePillContent(raw)).toBeNull()
  })
})

describe('suggestSlug', () => {
  it('lowercases and hyphenates', () => {
    expect(suggestSlug('Virtual Threads in 5 Minutes')).toBe('virtual-threads-in-5-minutes')
  })

  it('strips diacritics and symbols', () => {
    expect(suggestSlug('Café & Décor: 100%!')).toBe('cafe-decor-100')
  })

  it('collapses separators and trims hyphens', () => {
    expect(suggestSlug('  --hello   world--  ')).toBe('hello-world')
  })

  it('caps at 180 chars without a trailing hyphen', () => {
    const slug = suggestSlug(`${'word '.repeat(60)}end`)
    expect(slug.length).toBeLessThanOrEqual(180)
    expect(slug.endsWith('-')).toBe(false)
  })

  it('returns empty string for unusable titles', () => {
    expect(suggestSlug('!!!')).toBe('')
  })
})
