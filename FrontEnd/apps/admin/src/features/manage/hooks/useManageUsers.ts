import { useState } from 'react'
import { getAdminErrorMessage } from '../../../common/api/adminErrorMessage'
import {
    fetchManageUserDetail,
    fetchManageUsers,
    type ManageUserResponse,
} from '../manageApi'
import { isValidManageUserId, normalizeManageUserId } from '../utils/manageUserId'

export const useManageUsers = () => {
    const [message, setMessage] = useState('')
    const [users, setUsers] = useState<ManageUserResponse[] | null>(null)
    const [userDetail, setUserDetail] = useState<ManageUserResponse | null>(null)
    const [userId, setUserId] = useState('1')

    const loadUsers = async () => {
        setMessage('')

        try {
            const data = await fetchManageUsers()
            setUsers(data)
            setUserDetail(null)
        } catch (error) {
            setMessage(getAdminErrorMessage(error, '유저 목록 조회 실패'))
        }
    }

    const loadUserDetail = async () => {
        setMessage('')

        const trimmedUserId = normalizeManageUserId(userId)

        if (!trimmedUserId) {
            setMessage('User ID를 입력하세요.')
            return
        }

        if (!isValidManageUserId(trimmedUserId)) {
            setMessage('User ID는 숫자여야 합니다.')
            return
        }

        try {
            const data = await fetchManageUserDetail(trimmedUserId)
            setUserDetail(data)
            setMessage('유저 상세를 조회했습니다.')
        } catch (error) {
            setMessage(getAdminErrorMessage(error, '유저 상세 조회 실패'))
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
