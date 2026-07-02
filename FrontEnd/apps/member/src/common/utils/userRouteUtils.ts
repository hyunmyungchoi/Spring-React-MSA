import type { UserLogoutResponse } from "../types/userAuth";

// Chooses the browser redirect target after user logout.
export function getUserLogoutRedirectUrl(response: UserLogoutResponse): string {
    if (response.authServerLogoutRequired && response.authServerLogoutUrl) {
        return response.authServerLogoutUrl;
    }

    return "/auth";
}
