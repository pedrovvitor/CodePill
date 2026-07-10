import { useContext } from 'react'
import { PillApiContext } from './pill-api-context'
import type { PillApiPort } from '../ports/pill-api-port'

export function usePillApi(): PillApiPort {
  const api = useContext(PillApiContext)
  if (api === null) {
    throw new Error('usePillApi requires a PillApiContext provider (wired in src/app)')
  }
  return api
}
