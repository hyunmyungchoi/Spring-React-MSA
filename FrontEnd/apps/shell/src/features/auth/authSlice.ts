import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

type User = {
    id: string
    email: string
    name: string
    roles: string[]
}

type AuthState = {
    isAuthenticated: boolean
    accessToken: string | null
    user: User | null
}

const initialState: AuthState = {
    isAuthenticated: false,
    accessToken: null,
    user: null,
}

const authSlice = createSlice({
    name: 'auth',
    initialState,
    reducers: {
        loginSuccess: (
            state,
            action: PayloadAction<{ accessToken: string; user: User }>
        ) => {
            state.isAuthenticated = true
            state.accessToken = action.payload.accessToken
            state.user = action.payload.user
        },
        logout: (state) => {
            state.isAuthenticated = false
            state.accessToken = null
            state.user = null
        },
    },
})

export const { loginSuccess, logout } = authSlice.actions
export default authSlice.reducer