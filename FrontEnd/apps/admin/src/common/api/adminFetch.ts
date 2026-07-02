import { resolveAdminApiErrorMessage } from './adminApiContract'

const CSRF_COOKIE_NAME = 'ADMIN-XSRF-TOKEN'
const CSRF_HEADER_NAME = 'X-ADMIN-XSRF-TOKEN'
const CSRF_BOOTSTRAP_PATH = '/admin-bff/auth/me'

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
  const headers = await resolveAdminHeaders(path, options)

  const response = await fetch(path, {
    ...options,
    credentials: 'include',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (!response.ok) {
    const errorBody = await response.text().catch(() => '')
    throw new AdminFetchError(response.status, resolveAdminApiErrorMessage(errorBody, response.status))
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  return text ? (JSON.parse(text) as T) : (undefined as T)
}

async function resolveAdminHeaders(path: string, options: AdminFetchOptions) {
  const headers: HeadersInit = {
    Accept: 'application/json',
    ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
    ...options.headers,
  }

  if (!isUnsafeMethod(options.method) || !isAdminBffPath(path)) {
    return headers
  }

  let csrfToken = readCookie(CSRF_COOKIE_NAME)

  if (!csrfToken && path !== CSRF_BOOTSTRAP_PATH) {
    await fetch(CSRF_BOOTSTRAP_PATH, {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
      },
    }).catch(() => undefined)

    csrfToken = readCookie(CSRF_COOKIE_NAME)
  }

  if (csrfToken) {
    return {
      ...headers,
      [CSRF_HEADER_NAME]: csrfToken,
    }
  }

  return headers
}

function isUnsafeMethod(method?: string) {
  const resolvedMethod = method?.toUpperCase() ?? 'GET'
  return !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(resolvedMethod)
}

function isAdminBffPath(path: string) {
  return path.startsWith('/admin-bff/')
}

function readCookie(name: string) {
  if (typeof document === 'undefined') {
    return null
  }

  const cookie = document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(`${name}=`))

  if (!cookie) {
    return null
  }

  return decodeURIComponent(cookie.substring(name.length + 1))
}
