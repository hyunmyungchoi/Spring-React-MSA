import { useCallback } from 'react'
import { logoutAdminSession } from '../store/adminSessionSlice'
import { getAdminLogoutRedirectUrl } from '../utils/adminRouteUtils'
import { useAdminDispatch } from './useAdminStore'

// Exposes the admin logout flow with redirect handling.
export function useAdminLogout() {
  const dispatch = useAdminDispatch()

  return useCallback(async () => {
    const response = await dispatch(logoutAdminSession()).unwrap()
    window.location.href = getAdminLogoutRedirectUrl(response)
  }, [dispatch])
}
