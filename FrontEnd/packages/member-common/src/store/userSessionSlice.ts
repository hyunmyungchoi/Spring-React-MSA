import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import { fetchUserAuthMe, requestUserLogout } from "../api/userAuthApi";
import { USER_ERROR_MESSAGES } from "../messages/userErrorMessages";
import type { UserLogoutResponse } from "../types/userAuth";
import type { UserAuthMeResponse, UserSessionState } from "../types/userSession";
import { getUserErrorMessage } from "../utils/userErrorHandler";

const initialState: UserSessionState = {
    me: null,
    isAuthenticated: false,
    loading: true,
    error: null,
};

// Loads the current user session into Redux.
export const loadUserSession = createAsyncThunk<UserAuthMeResponse, void, { rejectValue: string }>("userSession/loadUserSession", async (_, { signal, rejectWithValue }) => {
    try {
        return await fetchUserAuthMe(signal);
    } catch (error) {
        return rejectWithValue(
            getUserErrorMessage(error, USER_ERROR_MESSAGES.SESSION_LOAD_FAILED)
        );
    }
});

// Logs out the user session through Redux.
export const logoutUserSession = createAsyncThunk<UserLogoutResponse, void, { rejectValue: string }>("userSession/logoutUserSession", async (_, { rejectWithValue }) => {
    try {
        return await requestUserLogout();
    } catch (error) {
        return rejectWithValue(
            getUserErrorMessage(error, USER_ERROR_MESSAGES.LOGOUT_FAILED)
        );
    }
});

// Stores user session state without persisting tokens.
const userSessionSlice = createSlice({
    name: "userSession",
    initialState,
    reducers: {
        clearUserSession(state) {
            state.me = null;
            state.isAuthenticated = false;
            state.loading = false;
            state.error = null;
        },
    },
    extraReducers: (builder) => {
        builder
            .addCase(loadUserSession.pending, (state) => {
                state.loading = true;
                state.error = null;
            })
            .addCase(loadUserSession.fulfilled, (state, action) => {
                state.me = action.payload.user;
                state.isAuthenticated = action.payload.authenticated;
                state.loading = false;
                state.error = action.payload.reason ?? null;
            })
            .addCase(loadUserSession.rejected, (state, action) => {
                state.me = null;
                state.isAuthenticated = false;
                state.loading = false;
                state.error = action.payload ?? USER_ERROR_MESSAGES.SESSION_LOAD_FAILED;
            })
            .addCase(logoutUserSession.pending, (state) => {
                state.loading = true;
                state.error = null;
            })
            .addCase(logoutUserSession.fulfilled, (state) => {
                state.me = null;
                state.isAuthenticated = false;
                state.loading = false;
                state.error = null;
            })
            .addCase(logoutUserSession.rejected, (state, action) => {
                state.loading = false;
                state.error = action.payload ?? USER_ERROR_MESSAGES.LOGOUT_FAILED;
            });
    },
});

export const { clearUserSession } = userSessionSlice.actions;
export default userSessionSlice.reducer;
