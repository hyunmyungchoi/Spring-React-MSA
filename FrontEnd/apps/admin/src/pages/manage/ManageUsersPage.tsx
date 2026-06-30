import ManageUserActionPanel from '../../features/manage/components/ManageUserActionPanel'
import ManageUsersSections from '../../features/manage/components/ManageUsersSections'
import { useManageUsers } from '../../features/manage/hooks/useManageUsers'

function ManageUsersPage() {
    const {
        message,
        users,
        userDetail,
        userId,
        setUserId,
        loadUsers,
        loadUserDetail,
    } = useManageUsers()

    return (
        <>
            <section className="admin-page-heading">
                <span>Manage</span>
                <h2>유저 관리</h2>
            </section>

            <ManageUserActionPanel
                userId={userId}
                onUserIdChange={setUserId}
                onLoadUsers={loadUsers}
                onLoadUserDetail={loadUserDetail}
            />

            <ManageUsersSections
                users={users}
                userDetail={userDetail}
                message={message}
            />
        </>
    )
}

export default ManageUsersPage
