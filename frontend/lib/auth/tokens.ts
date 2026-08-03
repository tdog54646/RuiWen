// 保存认证令牌的 localStorage key。
export const AUTH_TOKENS_STORAGE_KEY = "line_auth_tokens"
// 保存当前登录用户信息的 localStorage key。
export const AUTH_USER_STORAGE_KEY = "line_current_user"

// 前端认证令牌结构：accessToken 用于接口鉴权，refreshToken 用于续期。
export type AuthTokens = {
  accessToken: string
  refreshToken: string
  expiresAt: number
}

// 判断当前代码是否运行在浏览器环境，避免服务端渲染时访问 window/localStorage。
function isBrowser() {
  return typeof window !== "undefined"
}

// 从 localStorage 读取并校验已缓存的认证令牌。
export function readStoredTokens(): AuthTokens | null {
  if (!isBrowser()) return null
  try {
    const raw = localStorage.getItem(AUTH_TOKENS_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as AuthTokens
    // 缺少任一关键字段时视为无效缓存，交给调用方按未登录处理。
    if (!parsed.accessToken || !parsed.refreshToken || !parsed.expiresAt)
      return null
    return parsed
  } catch {
    // JSON 解析失败或 localStorage 读取异常时，不向外抛出错误。
    return null
  }
}

// 持久化认证令牌；传入 null 时清除已有令牌。
export function persistTokens(tokens: AuthTokens | null) {
  if (!isBrowser()) return
  if (!tokens) {
    localStorage.removeItem(AUTH_TOKENS_STORAGE_KEY)
    return
  }
  localStorage.setItem(AUTH_TOKENS_STORAGE_KEY, JSON.stringify(tokens))
}

// 从 localStorage 读取当前用户信息，并由调用方通过泛型指定返回类型。
export function readStoredUser<T>(): T | null {
  if (!isBrowser()) return null
  try {
    const raw = localStorage.getItem(AUTH_USER_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    // 用户缓存必须是对象，避免把异常值当作用户信息返回。
    if (!parsed || typeof parsed !== "object") return null
    return parsed as T
  } catch {
    // 缓存损坏时返回 null，让上层重新获取用户信息。
    return null
  }
}

// 持久化当前用户信息；传入 null 时清除已有用户缓存。
export function persistUser<T>(user: T | null) {
  if (!isBrowser()) return
  if (!user) {
    localStorage.removeItem(AUTH_USER_STORAGE_KEY)
    return
  }
  try {
    localStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify(user))
  } catch {
    // localStorage 写入失败不阻塞登录流程。
    // ignore
  }
}

// 清除认证令牌和当前用户缓存，通常用于退出登录或登录状态失效。
export function clearAll() {
  if (!isBrowser()) return
  localStorage.removeItem(AUTH_TOKENS_STORAGE_KEY)
  localStorage.removeItem(AUTH_USER_STORAGE_KEY)
}
