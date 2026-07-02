export type CommunityPost = {
    id: number;
    title: string;
    content: string;
    author: string;
    createdAt: string;
    updatedAt: string;
};

export type CommunityPostPayload = {
    title: string;
    content: string;
};
