type AdminFetchOptions = {
    method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
    signal?: AbortSignal
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

    return (await response.json()) as T
}