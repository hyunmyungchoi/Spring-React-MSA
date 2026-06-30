import { AdminFetchError } from './adminFetch'

export const getAdminErrorMessage = (
    error: unknown,
    fallbackMessage: string
): string => {
    if (error instanceof AdminFetchError) {
        return `${fallbackMessage}. ${error.message}`
    }

    return fallbackMessage
}
