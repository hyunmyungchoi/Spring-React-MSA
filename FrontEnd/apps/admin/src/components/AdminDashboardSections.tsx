import type { AdminMeResponse } from '../api/adminAuthApi'
import type { AdminUserResponse } from '../api/adminUserApi'
import AdminMeCard from './AdminMeCard'
import AdminUserMeCard from './AdminUserMeCard'
import AdminUsersTable from './AdminUsersTable'
import AdminUserDetailCard from './AdminUserDetailCard'

type AdminDashboardSectionsProps = {
    me: AdminMeResponse | null
    userMe: unknown
    adminUsers: AdminUserResponse[] | null
    adminUserDetail: AdminUserResponse | null
    message: string
}

function AdminDashboardSections({
                                    me,
                                    userMe,
                                    adminUsers,
                                    adminUserDetail,
                                    message,
                                }: AdminDashboardSectionsProps) {
    return (
        <>
            <section>
                <h2>Admin Me</h2>
                <AdminMeCard me={me} />
            </section>

            <section>
                <h2>Admin User Me</h2>
                <AdminUserMeCard userMe={userMe} />
            </section>

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

export default AdminDashboardSections