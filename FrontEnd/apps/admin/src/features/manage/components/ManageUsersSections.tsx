import MessageSection from '../../../common/components/MessageSection'
import type { ManageUserResponse } from '../manageApi'
import ManageUserDetailCard from './ManageUserDetailCard'
import ManageUsersTable from './ManageUsersTable'

type ManageUsersSectionsProps = {
    users: ManageUserResponse[] | null
    userDetail: ManageUserResponse | null
    message: string
}

function ManageUsersSections({
    users,
    userDetail,
    message,
}: ManageUsersSectionsProps) {
    return (
        <div className="admin-users-grid">
            <section className="admin-section admin-users-list-section">
                <div className="admin-section-heading">
                    <span>Manage</span>
                    <h2>유저 목록</h2>
                </div>
                <ManageUsersTable users={users} />
            </section>

            <section className="admin-section">
                <div className="admin-section-heading">
                    <span>Detail</span>
                    <h2>유저 상세</h2>
                </div>
                <ManageUserDetailCard user={userDetail} />
            </section>

            <MessageSection message={message} />
        </div>
    )
}

export default ManageUsersSections
