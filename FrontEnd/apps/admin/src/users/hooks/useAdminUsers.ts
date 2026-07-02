import { useState } from 'react'
import { ADMIN_ERROR_MESSAGES } from '../../common/messages/adminErrorMessages'
import { ADMIN_VALIDATION_MESSAGES } from '../../common/messages/adminValidationMessages'
import type { AdminUserResponse, MemberPresenceEventResponse, MemberSessionResponse } from '../../common/types/adminUser'
import { getAdminErrorMessage } from '../../common/utils/adminErrorHandler'
import { isValidAdminUserId, normalizeAdminUserId } from '../../common/utils/adminRouteUtils'
import { fetchAdminUserDetail, fetchAdminUsers, fetchMemberPresenceEvents, fetchMemberSessions } from '../api/adminUserApi'

// Manages admin user list and user detail lookup state.
export function useAdminUsers() {
  const [message, setMessage] = useState('')
  const [users, setUsers] = useState<AdminUserResponse[] | null>(null)
  const [userDetail, setUserDetail] = useState<AdminUserResponse | null>(null)
  const [memberSessions, setMemberSessions] = useState<MemberSessionResponse[] | null>(null)
  const [memberPresenceEvents, setMemberPresenceEvents] = useState<MemberPresenceEventResponse[] | null>(null)
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

  const loadMemberSessions = async () => {
    setMessage('')

    try {
      const data = await fetchMemberSessions()
      setMemberSessions(data)
      setMessage(`Member sessions loaded: ${data.length}`)
    } catch (error) {
      setMessage(getAdminErrorMessage(error, ADMIN_ERROR_MESSAGES.MEMBER_SESSION_LIST_LOAD_FAILED))
    }
  }

  const loadMemberPresenceEvents = async () => {
    setMessage('')

    try {
      const data = await fetchMemberPresenceEvents()
      setMemberPresenceEvents(data)
      setMessage(`Member presence events loaded: ${data.length}`)
    } catch (error) {
      setMessage(getAdminErrorMessage(error, ADMIN_ERROR_MESSAGES.MEMBER_PRESENCE_EVENT_LIST_LOAD_FAILED))
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
    memberSessions,
    memberPresenceEvents,
    userId,
    setUserId: changeUserId,
    loadUsers,
    loadUserDetail,
    loadMemberSessions,
    loadMemberPresenceEvents,
  }
}
