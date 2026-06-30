import type { AdminUserResponse } from '../../types/adminUser'

type AdminUserDetailCardProps = {
  user: AdminUserResponse | null
}

// Renders one admin-managed user detail record.
function AdminUserDetailCard({ user }: AdminUserDetailCardProps) {
  if (!user) {
    return <p className="admin-empty">상세 조회 결과가 없습니다.</p>
  }

  return (
    <dl className="admin-detail-list">
      <div>
        <dt>User ID</dt>
        <dd>{user.userId}</dd>
      </div>
      <div>
        <dt>Login ID</dt>
        <dd>{user.loginId}</dd>
      </div>
      <div>
        <dt>Email</dt>
        <dd>{user.email}</dd>
      </div>
      <div>
        <dt>Username</dt>
        <dd>{user.username}</dd>
      </div>
      <div>
        <dt>Enabled</dt>
        <dd>{user.enabled ? 'Y' : 'N'}</dd>
      </div>
      <div>
        <dt>Roles</dt>
        <dd>{user.roles.join(', ')}</dd>
      </div>
    </dl>
  )
}

export default AdminUserDetailCard
