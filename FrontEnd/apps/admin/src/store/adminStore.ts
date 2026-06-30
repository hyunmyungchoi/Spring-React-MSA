import { configureStore } from '@reduxjs/toolkit'
import adminSessionReducer from './adminSessionSlice'

// Creates the Redux store for the admin app.
export const adminStore = configureStore({
  reducer: {
    adminSession: adminSessionReducer,
  },
})

export type AdminRootState = ReturnType<typeof adminStore.getState>
export type AdminAppDispatch = typeof adminStore.dispatch
