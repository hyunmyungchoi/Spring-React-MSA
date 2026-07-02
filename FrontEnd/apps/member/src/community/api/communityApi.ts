import type { UserServiceMeResponse } from "../types/userSession";
import { userFetchJson } from "./userFetch";

// Loads the current user profile from the community service.
export function fetchCommunityMe(signal?: AbortSignal): Promise<UserServiceMeResponse> {
    return userFetchJson<UserServiceMeResponse>("/bff/community/me", { signal });
}
