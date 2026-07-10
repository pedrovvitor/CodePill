import { describe, expect, it } from 'vitest'
import { formatEstimatedDuration } from './pill'

describe('formatEstimatedDuration', () => {
  it('renders sub-minute durations in seconds', () => {
    expect(formatEstimatedDuration(45)).toBe('45 sec')
  })

  it('renders whole minutes', () => {
    expect(formatEstimatedDuration(60)).toBe('1 min')
    expect(formatEstimatedDuration(300)).toBe('5 min')
  })

  it('rounds partial minutes up (a 61s pill costs you 2 minutes of attention)', () => {
    expect(formatEstimatedDuration(61)).toBe('2 min')
  })

  it('renders hours with remaining minutes', () => {
    expect(formatEstimatedDuration(3600)).toBe('1 h')
    expect(formatEstimatedDuration(5400)).toBe('1 h 30 min')
  })
})
