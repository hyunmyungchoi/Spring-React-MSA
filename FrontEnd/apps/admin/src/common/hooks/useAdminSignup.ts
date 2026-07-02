import { signupAdmin } from '../api/adminAuthApi'

// Exposes the admin signup API command to forms.
export function useAdminSignup() {
  return {
    signup: signupAdmin,
  }
}
