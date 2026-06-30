import { ADMIN_GATEWAY_BASE_URL } from '../config/adminEnv'
import type { AdminApiErrorBody } from '../types/adminResponse'

type AdminFetchOptions = Omit<RequestInit, 'body' | 'credentials'> & {
  body?: unknown
}

export class AdminFetchError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
    this.name = 'AdminFetchError'
  }
}

// Sends a JSON request to the admin gateway with cookie credentials.
export async function adminFetchJson<T>(path: string, options: AdminFetchOptions = {}): Promise<T> {
  const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}${path}`, {
    ...options,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...options.headers,
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (!response.ok) {
    const errorBody = await response.text().catch(() => '')
    throw new AdminFetchError(response.status, resolveAdminErrorMessage(errorBody, response.status))
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  return text ? (JSON.parse(text) as T) : (undefined as T)
}

// Extracts the most useful error text from a failed admin gateway response.
function resolveAdminErrorMessage(errorBody: string, status: number) {
  if (!errorBody) {
    return `Admin API request failed: ${status}`
  }

  try {
    const parsed = JSON.parse(errorBody) as AdminApiErrorBody
    return parsed.message ?? parsed.detail ?? parsed.error ?? errorBody
  } catch {
    return errorBody
  }
}
