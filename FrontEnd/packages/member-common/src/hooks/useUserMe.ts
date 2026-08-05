import { useCallback } from "react";
import { loadUserSession } from "../store/userSessionSlice";
import { useUserDispatch, useUserSelector } from "./useUserStore";

// Exposes the user session state and reload action.
export function useUserMe() {
    const dispatch = useUserDispatch();
    const session = useUserSelector((state) => state.userSession);

    const loadMe = useCallback(() => {
        return dispatch(loadUserSession());
    }, [dispatch]);

    return {
        ...session,
        loadMe,
    };
}
