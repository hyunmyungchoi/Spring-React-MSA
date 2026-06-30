import type { UserServiceMeResponse } from "../types/userSession";
import { userFetchJson } from "./userFetch";

// Loads the current user profile from the stock service.
export function fetchStockMe(signal?: AbortSignal): Promise<UserServiceMeResponse> {
    return userFetchJson<UserServiceMeResponse>("/bff/stock/me", { signal });
}
