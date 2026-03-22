const BASE_URL = import.meta.env.VITE_API_URL || ''

function getAuthToken(): string | null {
  return localStorage.getItem('auth_token')
}

function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = { ...extra }
  const token = getAuthToken()
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  return headers
}

class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function handleUnauthorized(res: Response): void {
  if (res.status === 401) {
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_email')
    window.location.href = '/login'
    throw new ApiError(401, 'Unauthorized')
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  handleUnauthorized(res)
  if (!res.ok) {
    const body = await res.text()
    let message = `HTTP ${res.status}`
    try {
      const json = JSON.parse(body)
      message = json.error || json.message || message
    } catch {
      message = body || message
    }
    throw new ApiError(res.status, message)
  }
  const text = await res.text()
  if (!text) return undefined as T
  return JSON.parse(text)
}

export interface RequestOptions {
  signal?: AbortSignal
  timeoutMs?: number
}

function buildSignal(opts?: RequestOptions): AbortSignal | undefined {
  if (!opts) return undefined
  if (opts.signal && !opts.timeoutMs) return opts.signal
  if (!opts.signal && opts.timeoutMs) return AbortSignal.timeout(opts.timeoutMs)
  if (opts.signal && opts.timeoutMs) {
    return AbortSignal.any([opts.signal, AbortSignal.timeout(opts.timeoutMs)])
  }
  return undefined
}

function wrapFetchError(e: unknown): never {
  if (e instanceof DOMException && e.name === 'AbortError') {
    throw new ApiError(0, 'Запрос отменён')
  }
  if (e instanceof DOMException && e.name === 'TimeoutError') {
    throw new ApiError(0, 'Превышено время ожидания ответа от сервера')
  }
  throw e
}

export async function get<T>(path: string, opts?: RequestOptions): Promise<T> {
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      headers: authHeaders(),
      signal: buildSignal(opts),
    })
    return handleResponse<T>(res)
  } catch (e) {
    return wrapFetchError(e)
  }
}

export async function post<T>(path: string, body: unknown, opts?: RequestOptions): Promise<T> {
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body),
      signal: buildSignal(opts),
    })
    return handleResponse<T>(res)
  } catch (e) {
    return wrapFetchError(e)
  }
}

export async function postText(path: string, body: unknown, opts?: RequestOptions): Promise<string> {
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body),
      signal: buildSignal(opts),
    })
    handleUnauthorized(res)
    if (!res.ok) {
      const text = await res.text()
      throw new ApiError(res.status, text)
    }
    return res.text()
  } catch (e) {
    if (e instanceof ApiError) throw e
    return wrapFetchError(e)
  }
}

export async function del<T>(path: string, opts?: RequestOptions): Promise<T> {
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method: 'DELETE',
      headers: authHeaders(),
      signal: buildSignal(opts),
    })
    return handleResponse<T>(res)
  } catch (e) {
    return wrapFetchError(e)
  }
}

export async function postFile<T>(path: string, file: File, name?: string, opts?: RequestOptions): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  if (name) form.append('name', name)
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: authHeaders(),
      body: form,
      signal: buildSignal(opts),
    })
    return handleResponse<T>(res)
  } catch (e) {
    return wrapFetchError(e)
  }
}

export async function postBlob(path: string, body: unknown, filename: string, opts?: RequestOptions): Promise<void> {
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body),
      signal: buildSignal(opts),
    })
    handleUnauthorized(res)
    if (!res.ok) {
      const text = await res.text()
      throw new ApiError(res.status, text)
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (e) {
    if (e instanceof ApiError) throw e
    return wrapFetchError(e)
  }
}
