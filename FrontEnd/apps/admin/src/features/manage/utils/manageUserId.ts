export const normalizeManageUserId = (userId: string): string => {
    return userId.trim()
}

export const isValidManageUserId = (userId: string): boolean => {
    return /^\d+$/.test(normalizeManageUserId(userId))
}
