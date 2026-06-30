import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { adminStore } from './store/adminStore'
import './index.css'
import App from './app/App'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={adminStore}>
      <App />
    </Provider>
  </StrictMode>,
)
