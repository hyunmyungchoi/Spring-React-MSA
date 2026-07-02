export type AdminApiErrorBody = {
  success?: boolean
  status?: number
  data?: unknown
  message?: string | null
  detail?: string
  error?: string
}

export type AdminApiResponse<T> = {
  success: boolean
  status: number
  data: T
  message?: string | null
}

export class AdminApiContractError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
    this.name = 'AdminApiContractError'
  }
}

// Enforces the shared admin BFF envelope before feature code consumes data.
export function unwrapAdminApiResponse<T>(response: AdminApiResponse<T>): T {
  if (!response.success) {
    throw new AdminApiContractError(response.status, response.message ?? 'Admin API request failed')
  }

  return response.data
}

export function resolveAdminApiErrorMessage(errorBody: unknown, status: number) {
  if (!errorBody) {
    return `Admin API request failed: ${status}`
  }

  if (typeof errorBody === 'string') {
    try {
      return resolveAdminApiErrorMessage(JSON.parse(errorBody) as AdminApiErrorBody, status)
    } catch {
      return errorBody
    }
  }

  if (typeof errorBody === 'object') {
    const parsed = errorBody as AdminApiErrorBody
    return parsed.message ?? parsed.detail ?? parsed.error ?? `Admin API request failed: ${status}`
  }

  return `Admin API request failed: ${status}`
}
