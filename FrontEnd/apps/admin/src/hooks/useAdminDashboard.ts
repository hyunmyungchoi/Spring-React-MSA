import { useEffect, useState } from 'react'
import {fetchAdminMe,
    requestAdminLogout,
    type AdminMeResponse,
} from '../api/adminAuthApi'
import { fetchAdminUserMe } from '../api/adminUserApi'
import {AdminFetchError} from "../api/adminFetch.ts";
import { ADMIN_GATEWAY_BASE_URL } from '../config/adminEnv'

const getInitialMessage = (): string => {
    const params = new URLSearchParams(window.location.search)
    const error = params.get('error')

    return error ? `Admin login failed: ${error}` : ''
}

export const useAdminDashboard = () => {
    const [me, setMe] = useState<AdminMeResponse | null>(null)
    const [message, setMessage] = useState<string>(getInitialMessage)
    const [userMe, setUserMe] = useState<unknown>(null)


    const login = () => {
        window.location.href = `${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/login`
    }

    const loadMe = async () => {
        setMessage('')

        const data = await fetchAdminMe()
        setMe(data)
    }

    const logout = async () => {
        setMessage('')

        const data = await requestAdminLogout()

        setMe(null)
        setMessage(JSON.stringify(data, null, 2))

        if (data.authServerLogoutUrl) {
            window.location.href = data.authServerLogoutUrl
        }
    }

    const loadUserMe = async () => {
        setMessage('')

        try {
            const data = await fetchAdminUserMe()
            setUserMe(data)
        } catch (error) {
            if (error instanceof AdminFetchError) {
                setMessage(`Failed to load admin user me. status=${error.status}`)
                return
            }

            setMessage('Failed to load admin user me')
        }
    }



    useEffect(() => {
        const params = new URLSearchParams(window.location.search)
        const error = params.get('error')

        if (error) {
            window.history.replaceState({}, '', window.location.pathname)
            return
        }

        const controller = new AbortController()
        let ignored = false

        const loadInitialMe = async () => {
            try {
                const data = await fetchAdminMe(controller.signal)

                if (!ignored) {
                    setMe(data)
                }
            } catch (error) {
                if (ignored) {
                    return
                }

                if (error instanceof AdminFetchError) {
                    setMessage(`Failed to load admin session. status=${error.status}`)
                    return
                }

                setMessage('Failed to load admin session')
            }
        }

        void loadInitialMe()

        return () => {
            ignored = true
            controller.abort()
        }
    }, [])

    return {
        me,
        message,
        userMe,
        login,
        loadMe,
        logout,
        loadUserMe,
    }
}