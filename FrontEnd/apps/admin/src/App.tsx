import { BrowserRouter } from 'react-router-dom'
import AdminRouters from './AdminRouters'
import '@springmsa/admin-common/App.css'

// Mounts the admin web router.
function App() {
  return (
    <BrowserRouter>
      <AdminRouters />
    </BrowserRouter>
  )
}

export default App

