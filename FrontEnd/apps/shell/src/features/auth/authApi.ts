import { bffGet, bffPost } from "../../common/api/bffClient";

const BFF_BASE_URL =
    import.meta.env.VITE_BFF_BASE_URL ?? "http://localhost:8080";

export async function fetchAuthMe() {
    return bffGet<unknown>("/bff/auth/me");
}

export function redirectToLogin() {
    window.location.href = `${BFF_BASE_URL}/bff/auth/login`;
}

export async function logout() {
    return bffPost<void>("/bff/auth/logout");
}