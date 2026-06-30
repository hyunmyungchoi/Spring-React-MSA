import { AdminFetchError } from '../api/adminFetch'

// Converts unknown admin errors into display-ready messages.
export function getAdminErrorMessage(error: unknown, fallbackMessage: string): string {
  if (error instanceof AdminFetchError) {
    return error.message ? `${fallbackMessage}. ${error.message}` : fallbackMessage
  }

  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallbackMessage
}
