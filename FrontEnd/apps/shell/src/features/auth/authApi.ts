import { bffGet, bffPost } from "../../common/api/bffClient";

const BFF_BASE_URL =
    import.meta.env.VITE_BFF_BASE_URL ?? "http://localhost:8080";

type LogoutResponse = {
    logout: string;
    authServerLogoutRequired?: boolean;
    authServerLogoutUrl?: string;
};

export async function fetchAuthMe() {
    return bffGet<unknown>("/bff/auth/me");
}

export function redirectToLogin() {
    window.location.href = `${BFF_BASE_URL}/bff/auth/login`;
}

export async function logout() {
    const response = await bffPost<LogoutResponse>("/bff/auth/logout");

    if (response.authServerLogoutRequired && response.authServerLogoutUrl) {
        window.location.href = response.authServerLogoutUrl;
        return;
    }

    window.location.href = "/login";
}