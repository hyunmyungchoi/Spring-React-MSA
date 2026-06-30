import { Link } from 'react-router-dom'

// Renders the admin landing page after login.
function ManageHomePage() {
  return (
    <section className="admin-service-grid">
      <Link className="admin-service-tile admin-service-link" to="/manage/users">
        <span>Manage</span>
        <strong>User Management</strong>
      </Link>
    </section>
  )
}

export default ManageHomePage
