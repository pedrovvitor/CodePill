import type { Pill } from '../../domain/catalog/pill'
import { formatEstimatedDuration } from '../../domain/catalog/pill'
import { Badge } from '../design-system/Badge'

/** One pill in the vertical feed — a full-width, snap-aligned card. */
export function PillCard({ pill }: { pill: Pill }) {
  return (
    <article className="flex snap-start flex-col gap-3 rounded-2xl border border-surface-line bg-surface-raised p-5">
      <div className="flex items-center justify-between">
        <Badge>{pill.type}</Badge>
        <span className="text-sm text-slate-400">
          {formatEstimatedDuration(pill.estimatedDurationSeconds)}
        </span>
      </div>
      <h2 className="text-xl leading-snug font-semibold text-slate-50">{pill.title}</h2>
      {pill.summary !== null && <p className="text-slate-300">{pill.summary}</p>}
    </article>
  )
}
