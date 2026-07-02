import { useState } from 'react'
import { ADMIN_ERROR_MESSAGES } from '../../messages/adminErrorMessages'
import { ADMIN_VALIDATION_MESSAGES } from '../../messages/adminValidationMessages'
import type { AdminUserResponse } from '../../types/adminUser'
import { getAdminErrorMessage } from '../../utils/adminErrorHandler'
import { isValidAdminUserId, normalizeAdminUserId } from '../../utils/adminRouteUtils'
import { fetchAdminUserDetail, fetchAdminUsers } from '../api/adminUserApi'

// Manages admin user list and user detail lookup state.
export function useAdminUsers() {
  const [message, setMessage] = useState('')
  const [users, setUsers] = useState<AdminUserResponse[] | null>(null)
  const [userDetail, setUserDetail] = useState<AdminUserResponse | null>(null)
  const [userId, setUserId] = useState('1')

  const loadUsers = async () => {
    setMessage('')

    try {
      const data = await fetchAdminUsers()
      setUsers(data)
      setUserDetail(null)
    } catch (error) {
      setMessage(getAdminErrorMessage(error, ADMIN_ERROR_MESSAGES.USER_LIST_LOAD_FAILED))
    }
  }

  const loadUserDetail = async () => {
    setMessage('')

    const trimmedUserId = normalizeAdminUserId(userId)

    if (!trimmedUserId) {
      setMessage(ADMIN_VALIDATION_MESSAGES.USER_ID_REQUIRED)
      return
    }

    if (!isValidAdminUserId(trimmedUserId)) {
      setMessage(ADMIN_VALIDATION_MESSAGES.USER_ID_NUMBER_ONLY)
      return
    }

    try {
      const data = await fetchAdminUserDetail(trimmedUserId)
      setUserDetail(data)
      setMessage('User detail loaded.')
    } catch (error) {
      setMessage(getAdminErrorMessage(error, ADMIN_ERROR_MESSAGES.USER_DETAIL_LOAD_FAILED))
    }
  }

  const changeUserId = (nextUserId: string) => {
    setUserId(nextUserId)
    setUserDetail(null)
  }

  return {
    message,
    users,
    userDetail,
    userId,
    setUserId: changeUserId,
    loadUsers,
    loadUserDetail,
  }
}
