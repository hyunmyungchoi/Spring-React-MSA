import type { UserServiceMeResponse } from "../../types/userSession";
import { memberRequest } from "../../api/memberApiClient";

export type CommunityPost = {
    id: number;
    title: string;
    content: string;
    author: string;
    createdAt: string;
    updatedAt: string;
};

export type CommunityPostRequest = {
    title: string;
    content: string;
};

// Loads the current user profile from the community service.
export function fetchCommunityMe(signal?: AbortSignal): Promise<UserServiceMeResponse> {
    return memberRequest<UserServiceMeResponse>({ url: "/bff/community/me", signal });
}

export function fetchCommunityPosts(signal?: AbortSignal): Promise<CommunityPost[]> {
    return memberRequest<CommunityPost[]>({ url: "/bff/community/posts", signal });
}

export function createCommunityPost(request: CommunityPostRequest): Promise<CommunityPost> {
    return memberRequest<CommunityPost>({
        url: "/bff/community/posts",
        method: "POST",
        data: request,
    });
}

export function updateCommunityPost(postId: number, request: CommunityPostRequest): Promise<CommunityPost> {
    return memberRequest<CommunityPost>({
        url: `/bff/community/posts/${postId}`,
        method: "PUT",
        data: request,
    });
}

export function deleteCommunityPost(postId: number): Promise<void> {
    return memberRequest<void>({
        url: `/bff/community/posts/${postId}`,
        method: "DELETE",
    });
}
