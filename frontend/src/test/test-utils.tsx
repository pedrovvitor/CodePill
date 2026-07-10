import type { ReactElement, ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, renderHook } from '@testing-library/react'
import type { PillApiPort } from '../application/ports/pill-api-port'
import { PillApiContext } from '../application/pills/pill-api-context'
import { stubPillApi } from './fixtures'

export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
}

interface ProviderOptions {
  api?: PillApiPort
  queryClient?: QueryClient
  route?: string
}

export function makeWrapper({
  api = stubPillApi(),
  queryClient,
  route = '/',
}: ProviderOptions = {}) {
  const client = queryClient ?? createTestQueryClient()
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={[route]}>
        <QueryClientProvider client={client}>
          <PillApiContext value={api}>{children}</PillApiContext>
        </QueryClientProvider>
      </MemoryRouter>
    )
  }
}

export function renderWithProviders(ui: ReactElement, options: ProviderOptions = {}) {
  return render(ui, { wrapper: makeWrapper(options) })
}

export function renderHookWithProviders<T>(hook: () => T, options: ProviderOptions = {}) {
  return renderHook(hook, { wrapper: makeWrapper(options) })
}
