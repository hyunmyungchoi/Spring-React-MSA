import { useDispatch, useSelector } from 'react-redux'
import type { AdminAppDispatch, AdminRootState } from '../store/adminStore'

// Provides typed Redux hooks for the admin app.
export const useAdminDispatch = useDispatch.withTypes<AdminAppDispatch>()
export const useAdminSelector = useSelector.withTypes<AdminRootState>()
