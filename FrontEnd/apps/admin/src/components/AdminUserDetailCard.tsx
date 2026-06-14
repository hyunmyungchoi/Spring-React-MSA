import type { AdminUserResponse } from '../api/adminUserApi'

type AdminUserDetailCardProps = {
    user: AdminUserResponse | null
}

function AdminUserDetailCard({ user }: AdminUserDetailCardProps) {
    if (!user) {
        return <p>No data</p>
    }

    return (
        <div style={{ border: '1px solid #ccc', padding: 16, maxWidth: 720 }}>
            <p>
                <strong>User ID:</strong> {user.userId}
            </p>
            <p>
                <strong>Login ID:</strong> {user.loginId}
            </p>
            <p>
                <strong>Email:</strong> {user.email}
            </p>
            <p>
                <strong>Username:</strong> {user.username}
            </p>
            <p>
                <strong>Enabled:</strong> {user.enabled ? 'Y' : 'N'}
            </p>
            <p>
                <strong>Roles:</strong> {user.roles.join(', ')}
            </p>
        </div>
    )
}

export default AdminUserDetailCard