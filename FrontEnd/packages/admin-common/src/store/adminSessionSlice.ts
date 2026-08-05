import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { fetchAdminMe, requestAdminLogout } from '../api/adminAuthApi'
import { ADMIN_ERROR_MESSAGES } from '../messages/adminErrorMessages'
import type { AdminLogoutResponse } from '../types/adminAuth'
import type { AdminMeResponse, AdminSessionState } from '../types/adminSession'
import { getAdminErrorMessage } from '../utils/adminErrorHandler'

const initialState: AdminSessionState = {
  me: null,
  isAuthenticated: false,
  loading: true,
  error: null,
}

// Loads the current admin session into Redux.
export const loadAdminSession = createAsyncThunk<AdminMeResponse, void, { rejectValue: string }>('adminSession/loadAdminSession', async (_, { signal, rejectWithValue }) => {
  try {
    return await fetchAdminMe(signal)
  } catch (error) {
    return rejectWithValue(
      getAdminErrorMessage(error, ADMIN_ERROR_MESSAGES.SESSION_LOAD_FAILED),
    )
  }
})

// Logs out the admin session through Redux.
export const logoutAdminSession = createAsyncThunk<AdminLogoutResponse, void, { rejectValue: string }>('adminSession/logoutAdminSession', async (_, { rejectWithValue }) => {
  try {
    return await requestAdminLogout()
  } catch (error) {
    return rejectWithValue(
      getAdminErrorMessage(error, ADMIN_ERROR_MESSAGES.LOGOUT_FAILED),
    )
  }
})

// Stores admin session state without persisting tokens.
const adminSessionSlice = createSlice({
  name: 'adminSession',
  initialState,
  reducers: {
    clearAdminSession(state) {
      state.me = null
      state.isAuthenticated = false
      state.loading = false
      state.error = null
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loadAdminSession.pending, (state) => {
        state.loading = true
        state.error = null
      })
      .addCase(loadAdminSession.fulfilled, (state, action) => {
        state.me = action.payload.user
        state.isAuthenticated = action.payload.authenticated
        state.loading = false
        state.error = action.payload.reason
      })
      .addCase(loadAdminSession.rejected, (state, action) => {
        state.me = null
        state.isAuthenticated = false
        state.loading = false
        state.error = action.payload ?? ADMIN_ERROR_MESSAGES.SESSION_LOAD_FAILED
      })
      .addCase(logoutAdminSession.pending, (state) => {
        state.loading = true
        state.error = null
      })
      .addCase(logoutAdminSession.fulfilled, (state) => {
        state.me = null
        state.isAuthenticated = false
        state.loading = false
        state.error = null
      })
      .addCase(logoutAdminSession.rejected, (state, action) => {
        state.loading = false
        state.error = action.payload ?? ADMIN_ERROR_MESSAGES.LOGOUT_FAILED
      })
  },
})

export const { clearAdminSession } = adminSessionSlice.actions
export default adminSessionSlice.reducer
