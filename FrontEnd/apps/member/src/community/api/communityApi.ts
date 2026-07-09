import { memberRequest } from "../../common/api/memberApiClient";
import type { UserServiceMeResponse } from "../../common/types/userSession";
import type { CommunityPost, CommunityPostPayload } from "../types/communityPost";

// Loads the current user profile from the community service. 주석테스트 CI/CD 테스트
export function fetchCommunityMe(signal?: AbortSignal): Promise<UserServiceMeResponse> {
    return memberRequest<UserServiceMeResponse>({ url: "/bff/community/me", signal });
}

export function fetchCommunityPosts(signal?: AbortSignal): Promise<CommunityPost[]> {
    return memberRequest<CommunityPost[]>({ url: "/bff/community/posts", signal });
}

export function createCommunityPost(payload: CommunityPostPayload): Promise<CommunityPost> {
    return memberRequest<CommunityPost>({
        url: "/bff/community/posts",
        method: "POST",
        data: payload,
    });
}

export function updateCommunityPost(postId: number, payload: CommunityPostPayload): Promise<CommunityPost> {
    return memberRequest<CommunityPost>({
        url: `/bff/community/posts/${postId}`,
        method: "PUT",
        data: payload,
    });
}

export function deleteCommunityPost(postId: number): Promise<void> {
    return memberRequest<void>({
        url: `/bff/community/posts/${postId}`,
        method: "DELETE",
    });
}
