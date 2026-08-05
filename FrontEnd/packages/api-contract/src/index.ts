export type FieldError = {
  field: string
  message: string
}

export type MsaResponse<T> = {
  success: boolean
  code: string
  message: string
  status: number
  data: T | null
  errors: FieldError[]
}

export type MsaApiErrorBody = Partial<Omit<MsaResponse<unknown>, 'errors'>> & {
  errors?: FieldError[]
  detail?: string
  error?: string
}

export class ApiContractError extends Error {
  readonly status: number
  readonly code?: string
  readonly errors: FieldError[]

  constructor(status: number, message: string, code?: string, errors: FieldError[] = []) {
    super(message)
    this.status = status
    this.code = code
    this.errors = errors
    this.name = 'ApiContractError'
  }
}

export function unwrapApiResponse<T>(body: T | MsaResponse<T>): T {
  if (!isMsaResponse<T>(body)) {
    return body
  }

  if (!body.success) {
    throw new ApiContractError(body.status, body.message || 'API request failed', body.code, body.errors)
  }

  return body.data as T
}

export function resolveApiErrorMessage(errorBody: unknown, status: number): string {
  const parsed = parseApiErrorBody(errorBody)
  return parsed?.message ?? parsed?.detail ?? parsed?.error ?? `API request failed: ${status}`
}

function parseApiErrorBody(errorBody: unknown): MsaApiErrorBody | undefined {
  if (!errorBody) {
    return undefined
  }

  if (typeof errorBody === 'string') {
    try {
      return parseApiErrorBody(JSON.parse(errorBody) as unknown)
    } catch {
      return { message: errorBody }
    }
  }

  return typeof errorBody === 'object' ? errorBody as MsaApiErrorBody : undefined
}

function isMsaResponse<T>(body: T | MsaResponse<T>): body is MsaResponse<T> {
  if (typeof body !== 'object' || body === null) {
    return false
  }

  const candidate = body as Partial<MsaResponse<T>>
  return typeof candidate.success === 'boolean'
    && typeof candidate.status === 'number'
    && typeof candidate.code === 'string'
}
