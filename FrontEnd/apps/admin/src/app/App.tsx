import { BrowserRouter } from 'react-router-dom'
import AdminRoutes from '../routes/AdminRoutes'
import '../App.css'

// Mounts the admin web router.
function App() {
  return (
    <BrowserRouter>
      <AdminRoutes />
    </BrowserRouter>
  )
}

export default App
