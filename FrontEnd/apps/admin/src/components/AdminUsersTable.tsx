import type { AdminUserResponse } from '../api/adminUserApi'

type AdminUsersTableProps = {
    users: AdminUserResponse[] | null
}

function AdminUsersTable({ users }: AdminUsersTableProps) {
    if (!users || users.length === 0) {
        return <p>No data</p>
    }

    return (
        <table border={1} cellPadding={8} style={{ borderCollapse: 'collapse', minWidth: 720 }}>
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
)
}

export default AdminUsersTable