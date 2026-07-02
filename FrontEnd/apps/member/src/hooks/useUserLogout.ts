import { useCallback } from "react";
import { logoutUserSession } from "../store/userSessionSlice";
import { getUserLogoutRedirectUrl } from "../utils/userRouteUtils";
import { useUserDispatch } from "./useUserStore";

// Exposes the user logout flow with redirect handling.
export function useUserLogout() {
    const dispatch = useUserDispatch();

    return useCallback(async () => {
        const response = await dispatch(logoutUserSession()).unwrap();
        window.location.href = getUserLogoutRedirectUrl(response);
    }, [dispatch]);
}
