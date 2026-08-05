import { Link } from 'react-router-dom'

type AdminManageSelectCardProps = {
  to: string
  label: string
  title: string
}

// Renders an admin management navigation tile.
function AdminManageSelectCard({ to, label, title }: AdminManageSelectCardProps) {
  return (
    <Link className="admin-service-tile admin-service-link" to={to}>
      <span>{label}</span>
      <strong>{title}</strong>
    </Link>
  )
}

export default AdminManageSelectCard
