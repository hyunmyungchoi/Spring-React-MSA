import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import { fetchAuthMe, logout as requestLogout } from "./authApi";

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

export const loadCurrentUser = createAsyncThunk(
    "auth/loadCurrentUser",
    async () => {
        return await fetchAuthMe();
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
                console.log("[loadCurrentUser.fulfilled]", action.payload);

                state.user = action.payload;
                state.authenticated = action.payload !== null;
                state.loading = false;
                state.error = null;
            })
            .addCase(loadCurrentUser.rejected, (state, action) => {
                console.log("[loadCurrentUser.rejected]", action.error);

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