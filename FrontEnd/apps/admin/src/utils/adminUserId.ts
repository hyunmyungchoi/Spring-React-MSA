export const normalizeAdminUserId = (adminUserId: string): string => {
    return adminUserId.trim()
}

export const isValidAdminUserId = (adminUserId: string): boolean => {
    return /^\d+$/.test(normalizeAdminUserId(adminUserId))
}