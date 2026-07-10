import { createContext } from 'react'
import type { PillApiPort } from '../ports/pill-api-port'

/**
 * DI seam for the pill API port: `app/` provides the infrastructure
 * implementation; tests provide stubs. Components never see it directly —
 * they consume the use-case hooks.
 */
export const PillApiContext = createContext<PillApiPort | null>(null)
