import type { AdminUserResponse } from '../../types/adminUser'

type AdminUserTableProps = {
  users: AdminUserResponse[] | null
}

// Renders the admin-managed users table.
function AdminUserTable({ users }: AdminUserTableProps) {
  if (!users || users.length === 0) {
    return <p className="admin-empty">No users loaded.</p>
  }

  return (
    <div className="admin-table-wrap">
      <table className="admin-users-table">
        <thead>
          <tr>
            <th>User ID</th>
            <th>Login ID</th>
            <th>Email</th>
            <th>Username</th>
            <th>Enabled</th>
            <th>Roles</th>
          </tr>
        </thead>
        <tbody>
          {users.map((user) => (
            <tr key={user.userId}>
              <td>{user.userId}</td>
              <td>{user.loginId}</td>
              <td>{user.email}</td>
              <td>{user.username}</td>
              <td>{user.enabled ? 'Y' : 'N'}</td>
              <td>{user.roles.join(', ')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default AdminUserTable
