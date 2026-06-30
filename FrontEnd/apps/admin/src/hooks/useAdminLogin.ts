import {
  loginAdminWithPassword,
  sendAdminEmailOtp,
  verifyAdminEmailOtp,
} from '../api/adminAuthApi'

// Exposes admin login API commands to forms.
export function useAdminLogin() {
  return {
    loginWithPassword: loginAdminWithPassword,
    sendEmailOtp: sendAdminEmailOtp,
    verifyEmailOtp: verifyAdminEmailOtp,
  }
}
