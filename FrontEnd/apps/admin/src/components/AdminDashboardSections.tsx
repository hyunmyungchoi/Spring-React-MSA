import type { AdminMeResponse } from '../api/adminAuthApi'
import AdminMeCard from './AdminMeCard'
import AdminUserMeCard from './AdminUserMeCard'

type AdminDashboardSectionsProps = {
    me: AdminMeResponse | null
    userMe: unknown
    message: string
}

function AdminDashboardSections({
                                    me,
                                    userMe,
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
                <h2>Message</h2>
                <pre>{message || 'No message'}</pre>
            </section>
        </>
    )
}

export default AdminDashboardSections