import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { aPill } from '../../test/fixtures'
import { PillCard } from './PillCard'

describe('PillCard', () => {
  it('shows title, summary, type and human-readable duration', () => {
    render(<PillCard pill={aPill()} />)

    expect(
      screen.getByRole('heading', { name: 'Virtual Threads in 5 Minutes' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Loom without the mythology.')).toBeInTheDocument()
    expect(screen.getByText('ARTICLE')).toBeInTheDocument()
    expect(screen.getByText('5 min')).toBeInTheDocument()
  })

  it('omits the summary block when the pill has none', () => {
    render(<PillCard pill={aPill({ summary: null, title: 'No summary' })} />)

    expect(screen.getByRole('heading', { name: 'No summary' })).toBeInTheDocument()
    expect(screen.queryByText('Loom without the mythology.')).not.toBeInTheDocument()
  })
})
