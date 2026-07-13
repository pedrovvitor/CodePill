import { BrowserRouter } from 'react-router'
import { appConfig } from '../infrastructure/config/env'
import { initTelemetry } from '../infrastructure/telemetry/otel'
import { AppRoutes } from './router'
import { AppProviders } from './providers'

// Telemetry boots with the app so the very first navigation is traced
// (OBSERVABILITY.md §1: observable from the first commit).
initTelemetry({
  serviceName: 'codepill-web',
  appVersion: appConfig.appVersion,
  deploymentEnv: appConfig.deploymentEnv,
  otlpTracesUrl: appConfig.otlpTracesUrl,
  apiBaseUrl: appConfig.apiBaseUrl,
  traceSamplingRatio: appConfig.traceSamplingRatio,
})

export function App() {
  return (
    <BrowserRouter>
      <AppProviders>
        <AppRoutes />
      </AppProviders>
    </BrowserRouter>
  )
}
