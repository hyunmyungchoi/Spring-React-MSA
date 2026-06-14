type AdminFetchOptions = {
    method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
    signal?: AbortSignal
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
    })

    if (!response.ok) {
        throw new AdminFetchError(response.status, `Admin API request failed: ${response.status}`)
    }

    return (await response.json()) as T
}