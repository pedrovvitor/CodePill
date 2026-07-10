import { SpanStatusCode, trace, type Attributes, type Span } from '@opentelemetry/api'

const TRACER_NAME = 'codepill-web'

/**
 * Wraps a use case in an active span so browser journeys join backend traces
 * (OBSERVABILITY.md §2.3): low-cardinality span names, business context in
 * `codepill.*` attributes. Uses only the vendor-neutral OTel API — the SDK is
 * wired by `infrastructure/telemetry`.
 */
export async function traceUseCase<T>(
  name: string,
  attributes: Attributes,
  fn: (span: Span) => Promise<T>,
): Promise<T> {
  const tracer = trace.getTracer(TRACER_NAME)
  return tracer.startActiveSpan(name, { attributes }, async (span) => {
    try {
      const result = await fn(span)
      span.setStatus({ code: SpanStatusCode.OK })
      return result
    } catch (error) {
      span.setStatus({ code: SpanStatusCode.ERROR })
      if (error instanceof Error) span.recordException(error)
      throw error
    } finally {
      span.end()
    }
  })
}
