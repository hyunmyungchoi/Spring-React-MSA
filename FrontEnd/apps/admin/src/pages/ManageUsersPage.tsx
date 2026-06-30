import AdminUserActionPanel from '../components/users/AdminUserActionPanel'
import AdminUserDetailCard from '../components/users/AdminUserDetailCard'
import AdminUserTable from '../components/users/AdminUserTable'
import { useAdminUsers } from '../hooks/useAdminUsers'

// Renders admin user management tools.
function ManageUsersPage() {
  const {
    message,
    users,
    userDetail,
    userId,
    setUserId,
    loadUsers,
    loadUserDetail,
  } = useAdminUsers()

  return (
    <>
      <section className="admin-page-heading">
        <span>Manage</span>
        <h2>유저 관리</h2>
      </section>

      <AdminUserActionPanel
        userId={userId}
        onUserIdChange={setUserId}
        onLoadUsers={loadUsers}
        onLoadUserDetail={loadUserDetail}
      />

      <div className="admin-users-grid">
        <section className="admin-section admin-users-list-section">
          <div className="admin-section-heading">
            <span>Manage</span>
            <h2>유저 목록</h2>
          </div>
          <AdminUserTable users={users} />
        </section>

        <section className="admin-section">
          <div className="admin-section-heading">
            <span>Detail</span>
            <h2>유저 상세</h2>
          </div>
          <AdminUserDetailCard user={userDetail} />
        </section>

        {message && <p className="admin-message admin-section-message">{message}</p>}
      </div>
    </>
  )
}

export default ManageUsersPage
