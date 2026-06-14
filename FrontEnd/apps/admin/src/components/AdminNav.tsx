import { NavLink } from 'react-router-dom'

function AdminNav() {
    return (
        <nav style={{ display: 'flex', gap: 12, marginBottom: 24 }}>
            <NavLink to="/">Dashboard</NavLink>
            <NavLink to="/users">Users</NavLink>
        </nav>
    )
}

export default AdminNav