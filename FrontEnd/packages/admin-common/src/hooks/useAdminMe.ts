import { useCallback } from 'react'
import { loadAdminSession } from '../store/adminSessionSlice'
import { useAdminDispatch, useAdminSelector } from './useAdminStore'

// Exposes the admin session state and reload action.
export function useAdminMe() {
  const dispatch = useAdminDispatch()
  const session = useAdminSelector((state) => state.adminSession)

  const loadMe = useCallback(() => {
    return dispatch(loadAdminSession())
  }, [dispatch])

  return {
    ...session,
    loadMe,
  }
}
