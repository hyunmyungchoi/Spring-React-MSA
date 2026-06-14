import type { AdminMeResponse } from '../api/adminAuthApi'
import AdminMeCard from './AdminMeCard'
import AdminUserMeCard from './AdminUserMeCard'
import MessageSection from './MessageSection'

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

            <MessageSection message={message} />
        </>
    )
}

export default AdminDashboardSections