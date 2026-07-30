import { BrowserRouter } from 'react-router'
import { appConfig } from '../infrastructure/config/env'
import { AppRoutes } from './router'
import { AppProviders } from './providers'

// The OTel SDK (incl. zone.js) loads out of the critical path so it stays out
// of the initial chunk; fetch instrumentation is patched in before any API
// call — data fetching starts only after the OIDC redirect/token exchange.
void import('../infrastructure/telemetry/otel').then(({ initTelemetry }) =>
  initTelemetry({
    serviceName: 'codepill-web',
    appVersion: appConfig.appVersion,
    deploymentEnv: appConfig.deploymentEnv,
    otlpTracesUrl: appConfig.otlpTracesUrl,
    apiBaseUrl: appConfig.apiBaseUrl,
    traceSamplingRatio: appConfig.traceSamplingRatio,
  }),
)

export function App() {
  return (
    <BrowserRouter>
      <AppProviders>
        <AppRoutes />
      </AppProviders>
    </BrowserRouter>
  )
}
