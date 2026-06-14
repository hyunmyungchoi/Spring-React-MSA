type AdminUserMe = {
    sub?: string
    userId?: number
    loginId?: string
    email?: string
    roles?: string[]
}

type AdminUserMeCardProps = {
    userMe: unknown
}

function AdminUserMeCard({ userMe }: AdminUserMeCardProps) {
    if (!userMe) {
        return <p>No data</p>
    }

    const data = userMe as AdminUserMe

    return (
        <div style={{ border: '1px solid #ccc', padding: 16, maxWidth: 720 }}>
            <p>
                <strong>Sub:</strong> {data.sub ?? '-'}
            </p>
            <p>
                <strong>User ID:</strong> {data.userId ?? '-'}
            </p>
            <p>
                <strong>Login ID:</strong> {data.loginId ?? '-'}
            </p>
            <p>
                <strong>Email:</strong> {data.email ?? '-'}
            </p>
            <p>
                <strong>Roles:</strong> {data.roles?.join(', ') ?? '-'}
            </p>
        </div>
    )
}

export default AdminUserMeCard