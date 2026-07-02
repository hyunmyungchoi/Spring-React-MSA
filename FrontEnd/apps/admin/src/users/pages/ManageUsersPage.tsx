import AdminUserActionPanel from '../components/AdminUserActionPanel'
import AdminUserDetailCard from '../components/AdminUserDetailCard'
import AdminMemberPresenceEventTable from '../components/AdminMemberPresenceEventTable'
import AdminMemberSessionTable from '../components/AdminMemberSessionTable'
import AdminUserTable from '../components/AdminUserTable'
import { useAdminUsers } from '../hooks/useAdminUsers'

// Renders admin user management tools.
function ManageUsersPage() {
  const {
    message,
    users,
    userDetail,
    memberSessions,
    memberPresenceEvents,
    userId,
    setUserId,
    loadUsers,
    loadUserDetail,
    loadMemberSessions,
    loadMemberPresenceEvents,
  } = useAdminUsers()

  return (
    <>
      <section className="admin-page-heading">
        <span>Manage</span>
        <h2>User management</h2>
      </section>

      <AdminUserActionPanel
        userId={userId}
        onUserIdChange={setUserId}
        onLoadUsers={loadUsers}
        onLoadUserDetail={loadUserDetail}
        onLoadMemberSessions={loadMemberSessions}
        onLoadMemberPresenceEvents={loadMemberPresenceEvents}
      />

      <div className="admin-users-grid">
        <section className="admin-section admin-users-list-section">
          <div className="admin-section-heading">
            <span>Manage</span>
            <h2>User list</h2>
          </div>
          <AdminUserTable users={users} />
        </section>

        <section className="admin-section">
          <div className="admin-section-heading">
            <span>Detail</span>
            <h2>User detail</h2>
          </div>
          <AdminUserDetailCard user={userDetail} />
        </section>

        <section className="admin-section admin-member-sessions-section">
          <div className="admin-section-heading">
            <span>Sessions</span>
            <h2>Member sessions</h2>
          </div>
          <AdminMemberSessionTable sessions={memberSessions} />
        </section>

        <section className="admin-section admin-member-sessions-section">
          <div className="admin-section-heading">
            <span>Events</span>
            <h2>Member presence events</h2>
          </div>
          <AdminMemberPresenceEventTable events={memberPresenceEvents} />
        </section>

        {message && <p className="admin-message admin-section-message">{message}</p>}
      </div>
    </>
  )
}

export default ManageUsersPage
