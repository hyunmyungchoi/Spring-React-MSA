type AdminFetchOptions = {
    method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
    signal?: AbortSignal
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

export const adminFetchJson = async <T>(
    url: string,
    options: AdminFetchOptions = {}
): Promise<T> => {
    const response = await fetch(url, {
        method: options.method ?? 'GET',
        credentials: 'include',
        signal: options.signal,
        headers: {
            Accept: 'application/json',
            ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
        },
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })

    if (!response.ok) {
        const errorBody = await response.text().catch(() => '')
        throw new AdminFetchError(response.status, resolveAdminErrorMessage(errorBody, response.status))
    }

    return (await response.json()) as T
}

const resolveAdminErrorMessage = (errorBody: string, status: number) => {
    if (!errorBody) {
        return `Admin API request failed: ${status}`
    }

    try {
        const parsed = JSON.parse(errorBody) as {
            message?: string
            detail?: string
            error?: string
        }

        return parsed.message ?? parsed.detail ?? parsed.error ?? errorBody
    } catch {
        return errorBody
    }
}
