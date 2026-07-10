import { describe, expect, it } from 'vitest'
import { traceUseCase } from './tracing'

describe('traceUseCase (user-journey spans, OBSERVABILITY.md §2.3/§2.4)', () => {
  it('returns the wrapped result', async () => {
    const result = await traceUseCase('create-pill', { 'codepill.pill.type': 'ARTICLE' }, () =>
      Promise.resolve('ok'),
    )
    expect(result).toBe('ok')
  })

  it('rethrows failures after recording them', async () => {
    await expect(
      traceUseCase('create-pill', {}, () => Promise.reject(new Error('boom'))),
    ).rejects.toThrow('boom')
  })

  it('hands the active span to the callback for extra attributes', async () => {
    await traceUseCase('create-pill', {}, (span) => {
      expect(typeof span.setAttribute).toBe('function')
      return Promise.resolve(null)
    })
  })
})
