import type { AdminUserResponse } from '../api/adminUserApi'
import AdminUserDetailCard from './AdminUserDetailCard'
import AdminUsersTable from './AdminUsersTable'

type AdminUsersSectionsProps = {
    adminUsers: AdminUserResponse[] | null
    adminUserDetail: AdminUserResponse | null
    message: string
}

function AdminUsersSections({
                                adminUsers,
                                adminUserDetail,
                                message,
                            }: AdminUsersSectionsProps) {
    return (
        <>
            <section>
                <h2>Admin Users</h2>
                <AdminUsersTable users={adminUsers} />
            </section>

            <section>
                <h2>Admin User Detail</h2>
                <AdminUserDetailCard user={adminUserDetail} />
            </section>

            <section>
                <h2>Message</h2>
                <pre>{message || 'No message'}</pre>
            </section>
        </>
    )
}

export default AdminUsersSections