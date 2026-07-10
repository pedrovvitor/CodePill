export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <span role="status" aria-label={label} className="inline-flex items-center justify-center">
      <span className="size-6 animate-spin rounded-full border-2 border-surface-line border-t-brand-500" />
    </span>
  )
}
