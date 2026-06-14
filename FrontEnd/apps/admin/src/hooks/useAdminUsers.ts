import { useState } from 'react'
import {
    fetchAdminUserDetail,
    fetchAdminUsers,
    type AdminUserResponse,
} from '../api/adminUserApi'

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
        } catch {
            setMessage('Failed to load admin users')
        }
    }

    const loadAdminUserDetail = async () => {
        setMessage('')

        if (!adminUserId.trim()) {
            setMessage('User ID is required')
            return
        }

        try {
            const data = await fetchAdminUserDetail(adminUserId.trim())
            setAdminUserDetail(data)
        } catch {
            setMessage('Failed to load admin user detail')
        }
    }

    return {
        message,
        adminUsers,
        adminUserDetail,
        adminUserId,
        setAdminUserId,
        loadAdminUsers,
        loadAdminUserDetail,
    }
}