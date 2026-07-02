import { useDispatch, useSelector } from "react-redux";
import type { UserAppDispatch, UserRootState } from "../store/userStore";

// Provides typed Redux hooks for the user app.
export const useUserDispatch = useDispatch.withTypes<UserAppDispatch>();
export const useUserSelector = useSelector.withTypes<UserRootState>();
