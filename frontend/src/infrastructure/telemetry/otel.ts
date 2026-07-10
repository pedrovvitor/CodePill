import { ZoneContextManager } from '@opentelemetry/context-zone'
import { W3CTraceContextPropagator } from '@opentelemetry/core'
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { registerInstrumentations } from '@opentelemetry/instrumentation'
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch'
import { resourceFromAttributes } from '@opentelemetry/resources'
import { BatchSpanProcessor, WebTracerProvider } from '@opentelemetry/sdk-trace-web'

export interface TelemetryConfig {
  serviceName: string
  appVersion: string
  deploymentEnv: string
  otlpTracesUrl: string
  apiBaseUrl: string
}

/** Required resource attributes per OBSERVABILITY.md §2. */
export function buildTelemetryAttributes(config: TelemetryConfig): Record<string, string> {
  return {
    'service.name': config.serviceName,
    'service.version': config.appVersion,
    'deployment.environment': config.deploymentEnv,
  }
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * URLs that receive the W3C `traceparent` header (OBSERVABILITY.md §2.1) —
 * our API only, never third parties.
 */
export function tracePropagationTargets(config: TelemetryConfig): RegExp[] {
  return config.apiBaseUrl === ''
    ? [/^\/api\//]
    : [new RegExp(`^${escapeRegExp(config.apiBaseUrl)}/api/`)]
}

let registeredProvider: WebTracerProvider | null = null

/**
 * Boots browser tracing: WebTracerProvider → OTLP/HTTP → collector :4318
 * (→ Tempo), with fetch auto-instrumentation so browser spans join backend
 * traces. Idempotent — subsequent calls return the registered provider.
 */
export function initTelemetry(config: TelemetryConfig): WebTracerProvider {
  if (registeredProvider !== null) return registeredProvider

  const provider = new WebTracerProvider({
    resource: resourceFromAttributes(buildTelemetryAttributes(config)),
    spanProcessors: [new BatchSpanProcessor(new OTLPTraceExporter({ url: config.otlpTracesUrl }))],
  })

  provider.register({
    contextManager: new ZoneContextManager(),
    propagator: new W3CTraceContextPropagator(),
  })

  registerInstrumentations({
    instrumentations: [
      new FetchInstrumentation({
        propagateTraceHeaderCorsUrls: tracePropagationTargets(config),
        // Never trace the trace exporter itself.
        ignoreUrls: [new RegExp(escapeRegExp(config.otlpTracesUrl))],
      }),
    ],
  })

  registeredProvider = provider
  return provider
}
