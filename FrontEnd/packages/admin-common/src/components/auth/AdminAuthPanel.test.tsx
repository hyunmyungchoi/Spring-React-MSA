import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import AdminAuthPanel from './AdminAuthPanel'

vi.mock('./AdminLoginForm', () => ({
  default: () => <form aria-label="admin login form" />,
}))

describe('AdminAuthPanel', () => {
  it('renders login without exposing administrator registration', () => {
    render(<AdminAuthPanel />)

    expect(screen.getByRole('heading', { name: 'Admin login' })).toBeInTheDocument()
    expect(screen.getByRole('form', { name: 'admin login form' })).toBeInTheDocument()
    expect(screen.queryByText(/sign up|register|registration/i)).not.toBeInTheDocument()
  })
})
