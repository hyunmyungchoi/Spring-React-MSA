import { configureStore } from "@reduxjs/toolkit";
import userSessionReducer from "./userSessionSlice";

// Creates the Redux store for the user app.
export const userStore = configureStore({
    reducer: {
        userSession: userSessionReducer,
    },
});

export type UserRootState = ReturnType<typeof userStore.getState>;
export type UserAppDispatch = typeof userStore.dispatch;
