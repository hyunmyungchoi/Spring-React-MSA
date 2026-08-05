export type CommunityPost = {
    id: number;
    title: string;
    content: string;
    author: string;
    ownedByCurrentUser: boolean;
    createdAt: string;
    updatedAt: string;
};

export type CommunityPostPayload = {
    title: string;
    content: string;
};

