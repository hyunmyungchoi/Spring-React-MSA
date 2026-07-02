import { loginAdminWithPassword } from '../api/adminAuthApi'

// Exposes admin login API commands to forms.
export function useAdminLogin() {
  return {
    loginWithPassword: loginAdminWithPassword,
  }
}
