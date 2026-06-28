import { useState } from 'react'
import {
    fetchAdminUserDetail,
    fetchAdminUsers,
    type AdminUserResponse,
} from '../api/adminUserApi'
import { getAdminErrorMessage } from '../api/adminErrorMessage'
import { isValidAdminUserId, normalizeAdminUserId } from '../utils/adminUserId'

export const useAdminUsers = () => {
    const [message, setMessage] = useState<string>('')
    const [adminUsers, setAdminUsers] = useState<AdminUserResponse[] | null>(null)
    const [adminUserDetail, setAdminUserDetail] = useState<AdminUserResponse | null>(null)
    const [adminUserId, setAdminUserId] = useState<string>('1')

    const loadAdminUsers = async () => {
        setMessage('')

        try {
            const data = await fetchAdminUsers()
            setAdminUsers(data)
            setAdminUserDetail(null)
        } catch (error) {
            setMessage(getAdminErrorMessage(error, 'Failed to load admin users'))
        }
    }

    const loadAdminUserDetail = async () => {
        setMessage('')

        const trimmedUserId = normalizeAdminUserId(adminUserId)

        if (!trimmedUserId) {
            setMessage('Admin user id is required')
            return
        }

        if (!isValidAdminUserId(trimmedUserId)) {
            setMessage('Admin user id must be numeric')
            return
        }

        try {
            const data = await fetchAdminUserDetail(trimmedUserId)
            setAdminUserDetail(data)
            setMessage('Loaded admin user detail')
        } catch (error) {
            setMessage(getAdminErrorMessage(error, 'Failed to load admin user detail'))
        }
    }

    const changeAdminUserId = (nextAdminUserId: string) => {
        setAdminUserId(nextAdminUserId)
        setAdminUserDetail(null)
    }

    return {
        message,
        adminUsers,
        adminUserDetail,
        adminUserId,
        setAdminUserId: changeAdminUserId,
        loadAdminUsers,
        loadAdminUserDetail,
    }
}