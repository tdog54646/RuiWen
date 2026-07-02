const getBaseUrl = () => {
  if (typeof window !== "undefined") return ""
  const base = process.env.NEXT_PUBLIC_API_BASE_URL
  if (!base) throw new Error("Missing NEXT_PUBLIC_API_BASE_URL")
  return base.replace(/\/+$/, "")
}

export type ApiFetchOptions = {
  method?: string
  headers?: Record<string, string>
  body?: unknown
  accessToken?: string | null
  signal?: AbortSignal
}

export class ApiError extends Error {
  readonly status: number
  readonly data: unknown

  constructor(status: number, message: string, data?: unknown) {
    super(message)
    this.name = "ApiError"
    this.status = status
    this.data = data
  }
}

function getStoredAccessToken(): string | null {
  if (typeof window === "undefined") return null
  try {
    const raw = localStorage.getItem("line_auth_tokens")
    if (!raw) return null
    const parsed = JSON.parse(raw) as { accessToken?: string }
    return parsed.accessToken ?? null
  } catch {
    return null
  }
}

function buildApiRequest(path: string, options: ApiFetchOptions = {}) {
  const baseUrl = getBaseUrl()
  const { method = "GET", headers = {}, body, accessToken, signal } = options

  const isFormData = typeof FormData !== "undefined" && body instanceof FormData

  const mergedHeaders: Record<string, string> = {
    ...(isFormData ? {} : { "Content-Type": "application/json" }),
    ...headers,
  }

  const token = accessToken === undefined ? getStoredAccessToken() : accessToken
  if (token) {
    mergedHeaders.Authorization = `Bearer ${token}`
  }

  const methodUpper = method.toUpperCase()
  const isIdempotent =
    methodUpper === "GET" || methodUpper === "HEAD" || methodUpper === "OPTIONS"
  if (!isIdempotent && typeof document !== "undefined") {
    try {
      const cookies = document.cookie ?? ""
      const match = cookies.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
      const xsrfToken = match ? decodeURIComponent(match[1]) : null
      if (xsrfToken && !("X-XSRF-TOKEN" in mergedHeaders)) {
        mergedHeaders["X-XSRF-TOKEN"] = xsrfToken
      }
    } catch {
      // ignore
    }
  }

  const url = baseUrl ? `${baseUrl}${path}` : path
  return {
    url,
    init: {
      method,
      headers: mergedHeaders,
      body: isFormData
        ? (body as FormData)
        : body
          ? JSON.stringify(body)
          : undefined,
      signal,
      credentials: "include",
    } satisfies RequestInit,
  }
}

async function throwApiError(response: Response): Promise<never> {
  let rawText = ""
  try {
    rawText = await response.text()
  } catch {
    rawText = ""
  }
  let errorData: unknown = rawText
  if (rawText) {
    try {
      errorData = JSON.parse(rawText)
    } catch {
      // keep raw text
    }
  }
  const message =
    typeof errorData === "object" &&
    errorData !== null &&
    "message" in errorData
      ? (errorData as { message: string }).message
      : rawText || `请求失败：${response.status}`
  throw new ApiError(response.status, message, errorData)
}

export async function apiFetchResponse(
  path: string,
  options: ApiFetchOptions = {},
): Promise<Response> {
  const { url, init } = buildApiRequest(path, options)
  const response = await fetch(url, init)

  if (!response.ok) {
    await throwApiError(response)
  }

  return response
}

export async function apiFetch<TResponse>(
  path: string,
  options: ApiFetchOptions = {},
): Promise<TResponse> {
  const response = await apiFetchResponse(path, options)

  if (response.status === 204) {
    return undefined as TResponse
  }

  const contentType = response.headers.get("content-type")
  if (contentType?.includes("application/json")) {
    return (await response.json()) as TResponse
  }

  return (await response.text()) as TResponse
}
