import { Link, useParams } from 'react-router'
import { isApiError } from '../../application/ports/pill-api-port'
import { usePill } from '../../application/pills/use-pill'
import { usePublishPill } from '../../application/pills/use-publish-pill'
import { formatEstimatedDuration } from '../../domain/catalog/pill'
import { RoleGate } from '../components/RoleGate'
import { Badge } from '../design-system/Badge'
import { Button } from '../design-system/Button'
import { Spinner } from '../design-system/Spinner'

function Content({ value }: { value: unknown }) {
  // Text remains text, including HTML and Markdown. Unknown block types stay
  // inspectable instead of silently losing authored content or executing it.
  if (typeof value === 'string') {
    return <p className="whitespace-pre-wrap break-words text-slate-200">{value}</p>
  }
  if (value !== null && typeof value === 'object') {
    if ('body' in value && typeof value.body === 'string') return <Content value={value.body} />
    if ('blocks' in value && Array.isArray(value.blocks)) {
      return (
        <>
          {value.blocks.map((block: unknown, index: number) => (
            <Content key={index} value={block} />
          ))}
        </>
      )
    }
  }
  return (
    <pre className="overflow-x-auto whitespace-pre-wrap break-words text-sm text-slate-300">
      {JSON.stringify(value, null, 2)}
    </pre>
  )
}

/** Detail access/ownership and publication are enforced again by the API. */
export function PillDetailPage() {
  const { id = '' } = useParams()
  const detail = usePill(id)
  const publish = usePublishPill()

  return (
    <section className="flex min-w-0 flex-col gap-5">
      <Link to="/" className="text-sm font-medium text-brand-100 underline">
        Back to feed
      </Link>
      {detail.isPending ? (
        <Spinner label="Loading pill" />
      ) : detail.isError ? (
        <div role="alert" className="flex flex-col items-start gap-4">
          {isApiError(detail.error) && detail.error.status === 404 ? (
            <p>This pill is unavailable or you do not have access.</p>
          ) : (
            <>
              <p>The pill could not be loaded.</p>
              <Button onClick={() => void detail.refetch()}>Try again</Button>
            </>
          )}
        </div>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <Badge>{detail.data.type}</Badge>
            <Badge>{detail.data.status}</Badge>
            <span className="text-sm text-slate-400">
              {formatEstimatedDuration(detail.data.estimatedDurationSeconds)}
            </span>
          </div>
          <h1 className="break-words text-3xl font-semibold text-slate-50">{detail.data.title}</h1>
          <article
            aria-label="Full pill content"
            className="flex min-w-0 flex-col gap-5 leading-relaxed"
          >
            <Content value={detail.data.content} />
          </article>
          {publish.isSuccess && <p role="status">Published and available in the feed.</p>}
          {publish.isError && (
            <p role="alert">Publication failed. Check your access and try again.</p>
          )}
          {(detail.data.status === 'DRAFT' || detail.data.status === 'IN_REVIEW') && (
            <RoleGate role="CURATOR">
              <Button disabled={publish.isPending} onClick={() => publish.mutate(detail.data.id)}>
                {publish.isPending ? 'Publishing…' : 'Publish pill'}
              </Button>
            </RoleGate>
          )}
        </>
      )}
    </section>
  )
}
