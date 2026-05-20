import { bffGet } from "../../common/api/bffClient";

export async function fetchCommunityMe() {
    return bffGet<unknown>("/bff/community/me");
}