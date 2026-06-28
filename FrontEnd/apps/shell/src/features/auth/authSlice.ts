import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import { fetchAuthMe, logout as requestLogout } from "./authApi";

type AuthMeResponse = {
    authenticated: boolean;
    user: unknown | null;
};

type AuthState = {
    user: unknown | null;
    authenticated: boolean;
    loading: boolean;
    error: string | null;
};

const initialState: AuthState = {
    user: null,
    authenticated: false,
    loading: true,
    error: null,
};

export const loadCurrentUser = createAsyncThunk<AuthMeResponse>(
    "auth/loadCurrentUser",
    async () => {
        return await fetchAuthMe() as AuthMeResponse;
    }
);

export const logoutCurrentUser = createAsyncThunk(
    "auth/logoutCurrentUser",
    async () => {
        await requestLogout();
    }
);

const authSlice = createSlice({
    name: "auth",
    initialState,
    reducers: {
        clearAuth(state) {
            state.user = null;
            state.authenticated = false;
            state.loading = false;
            state.error = null;
        },
    },
    extraReducers: (builder) => {
        builder
            .addCase(loadCurrentUser.pending, (state) => {
                state.loading = true;
                state.error = null;
            })
            .addCase(loadCurrentUser.fulfilled, (state, action) => {
                state.user = action.payload.user;
                state.authenticated = action.payload.authenticated;
                state.loading = false;
                state.error = null;
            })
            .addCase(loadCurrentUser.rejected, (state) => {
                state.user = null;
                state.authenticated = false;
                state.loading = false;
                state.error = "Not authenticated";
            })
            .addCase(logoutCurrentUser.fulfilled, (state) => {
                state.user = null;
                state.authenticated = false;
                state.loading = false;
                state.error = null;
            });
    },
});

export const { clearAuth } = authSlice.actions;
export default authSlice.reducer;