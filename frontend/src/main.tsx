import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { App } from './app/App'

const root = document.getElementById('root')
if (root === null) throw new Error('Root element #root is missing in index.html')

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
