import AdminManageSelectCard from '../components/manage/AdminManageSelectCard'

// Renders the admin landing page after login.
function ManageHomePage() {
  return (
    <section className="admin-service-grid">
      <AdminManageSelectCard
        to="/manage/users"
        label="유저 관리"
        title="User Management"
      />
      <AdminManageSelectCard
        to="/manage/logs"
        label="로그 관리"
        title="Log Management"
      />
    </section>
  )
}

export default ManageHomePage
