import type { AdminMeResponse } from '../api/adminAuthApi'

type AdminMeCardProps = {
    me: AdminMeResponse | null
}

function AdminMeCard({ me }: AdminMeCardProps) {
    if (!me) {
        return <p>No data</p>
    }

    if (!me.authenticated) {
        return (
            <div style={{ border: '1px solid #ccc', padding: 16, maxWidth: 720 }}>
                <p>
                    <strong>Authenticated:</strong> N
                </p>
                <p>
                    <strong>Reason:</strong> {me.reason ?? 'No reason'}
                </p>
            </div>
        )
    }

    return (
        <div style={{ border: '1px solid #ccc', padding: 16, maxWidth: 720 }}>
            <p>
                <strong>Authenticated:</strong> Y
            </p>
            <p>
                <strong>Sub:</strong> {me.user?.sub ?? '-'}
            </p>
            <p>
                <strong>Name:</strong> {me.user?.name ?? '-'}
            </p>
            <p>
                <strong>User ID:</strong> {me.user?.userId ?? '-'}
            </p>
            <p>
                <strong>Login ID:</strong> {me.user?.loginId ?? '-'}
            </p>
            <p>
                <strong>Email:</strong> {me.user?.email ?? '-'}
            </p>
            <p>
                <strong>Roles:</strong> {me.user?.roles?.join(', ') ?? '-'}
            </p>
        </div>
    )
}

export default AdminMeCard