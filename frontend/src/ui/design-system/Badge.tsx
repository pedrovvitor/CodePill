import type { ReactNode } from 'react'

export function Badge({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex items-center rounded-full bg-brand-600/20 px-2.5 py-0.5 text-xs font-semibold tracking-wide text-brand-100">
      {children}
    </span>
  )
}
