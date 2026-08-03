"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"

import { ApiError } from "@/lib/api/client"
import { authService } from "@/lib/api/auth"
import type {
  AuthenticatedUser,
  LoginRequest,
  RegisterRequest,
  TokenResponse,
} from "@/lib/types/auth"
import {
  AUTH_TOKENS_STORAGE_KEY,
  AUTH_USER_STORAGE_KEY,
  type AuthTokens,
  clearAll,
  persistTokens,
  persistUser,
  readStoredTokens,
  readStoredUser,
} from "@/lib/auth/tokens"
import { AuthProvider as ContextProvider, type AuthContextValue } from "./auth-context"

const ACCESS_TOKEN_SKEW_MS = 5_000
const AUTH_REFRESH_LOCK = "line-auth-refresh"
const PEER_REFRESH_WAIT_MS = 2_000

function parseInstantToMillis(value: string): number {
  const numeric = Number(value)
  if (!Number.isNaN(numeric)) {
    return numeric > 1e12 ? numeric : numeric * 1000
  }
  const t = Date.parse(value)
  return Number.isNaN(t) ? Date.now() + 10 * 60 * 1000 : t
}

function toTokens(token: TokenResponse): AuthTokens {
  return {
    accessToken: token.accessToken,
    refreshToken: token.refreshToken,
    expiresAt: parseInstantToMillis(token.accessTokenExpiresAt),
  }
}

function hasUsableAccessToken(tokens: AuthTokens) {
  return Date.now() < tokens.expiresAt - ACCESS_TOKEN_SKEW_MS
}

function isAuthenticationFailure(error: unknown) {
  if (!(error instanceof ApiError)) return false
  if (error.status === 401 || error.status === 403) return true
  if (typeof error.data !== "object" || error.data === null || !("code" in error.data)) {
    return false
  }
  const code = (error.data as { code?: unknown }).code
  // 兼容后端旧版本曾把这些会话错误映射为 HTTP 400 的行为，保证滚动部署安全。
  return (
    code === "REFRESH_TOKEN_INVALID" ||
    code === "IDENTIFIER_NOT_FOUND" ||
    code === "USER_BANNED"
  )
}

/**
 * Web Locks 在不同标签页之间串行化 Refresh Token 的单次消费。旧浏览器没有
 * Web Locks 时仍执行任务，失败方会通过 waitForPeerRefresh 接收胜出标签页的新令牌。
 */
async function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== "undefined" && navigator.locks) {
    return navigator.locks.request(AUTH_REFRESH_LOCK, task)
  }
  return task()
}

function waitForPeerRefresh(previousRefreshToken: string): Promise<AuthTokens | null> {
  if (typeof window === "undefined") return Promise.resolve(null)

  const current = readStoredTokens()
  if (current && current.refreshToken !== previousRefreshToken) {
    return Promise.resolve(current)
  }

  return new Promise((resolve) => {
    let settled = false
    const finish = (tokens: AuthTokens | null) => {
      if (settled) return
      settled = true
      window.removeEventListener("storage", onStorage)
      window.clearInterval(poll)
      window.clearTimeout(timeout)
      resolve(tokens)
    }
    const check = () => {
      const next = readStoredTokens()
      if (next && next.refreshToken !== previousRefreshToken) finish(next)
    }
    const onStorage = (event: StorageEvent) => {
      if (event.key === AUTH_TOKENS_STORAGE_KEY || event.key === null) check()
    }
    const poll = window.setInterval(check, 50)
    const timeout = window.setTimeout(() => finish(null), PEER_REFRESH_WAIT_MS)
    window.addEventListener("storage", onStorage)
    check()
  })
}

export function AuthProviderWrapper({ children }: { children: React.ReactNode }) {
  const initialTokensRef = useRef<AuthTokens | null>(null)
  if (initialTokensRef.current === null) {
    initialTokensRef.current = readStoredTokens()
  }

  const [tokens, setTokens] = useState<AuthTokens | null>(() => initialTokensRef.current)
  const [user, setUser] = useState<AuthenticatedUser | null>(() =>
    readStoredUser<AuthenticatedUser>(),
  )
  const [isLoading, setIsLoading] = useState(() => initialTokensRef.current != null)
  const [hydrated, setHydrated] = useState(false)
  const tokensRef = useRef<AuthTokens | null>(initialTokensRef.current)
  const refreshPromiseRef = useRef<Promise<AuthTokens | null> | null>(null)
  const restoreStartedRef = useRef(false)

  useEffect(() => setHydrated(true), [])

  const adoptTokens = useCallback((nextTokens: AuthTokens) => {
    tokensRef.current = nextTokens
    setTokens(nextTokens)
    persistTokens(nextTokens)
    return nextTokens
  }, [])

  const invalidateSession = useCallback(() => {
    tokensRef.current = null
    setTokens(null)
    setUser(null)
    clearAll()
  }, [])

  const fetchUser = useCallback(async (accessToken: string) => {
    const profile = await authService.fetchCurrentUser(accessToken)
    setUser(profile)
    persistUser(profile)
    return profile
  }, [])

  const refreshTokens = useCallback(
    async (force = false): Promise<AuthTokens | null> => {
      if (refreshPromiseRef.current) return refreshPromiseRef.current

      const startingTokens = tokensRef.current ?? readStoredTokens()
      if (!startingTokens) return null

      const task = withRefreshLock(async () => {
        // 必须在取得跨标签页锁后重读 storage。若另一个标签页已经轮换成功，
        // 直接采用其结果，绝不能再次消费旧 Refresh Token。
        const stored = readStoredTokens() ?? tokensRef.current
        if (!stored) return null
        if (stored.refreshToken !== startingTokens.refreshToken) {
          return adoptTokens(stored)
        }
        if (!force && hasUsableAccessToken(stored)) {
          return adoptTokens(stored)
        }

        try {
          const result = await authService.refresh(stored.refreshToken)
          return adoptTokens(toTokens(result))
        } catch (error) {
          if (isAuthenticationFailure(error)) {
            // 无 Web Locks 的旧浏览器中可能已经有另一个标签页消费成功。
            // 等待它写入轮换后的令牌，而不是清除共享 localStorage。
            const peerTokens = await waitForPeerRefresh(stored.refreshToken)
            if (peerTokens) return adoptTokens(peerTokens)
          }
          throw error
        }
      }).finally(() => {
        refreshPromiseRef.current = null
      })

      refreshPromiseRef.current = task
      return task
    },
    [adoptTokens],
  )

  const restoreSession = useCallback(async () => {
    const stored = readStoredTokens()
    if (!stored) return

    try {
      let active = stored
      if (!hasUsableAccessToken(active)) {
        const refreshed = await refreshTokens()
        if (!refreshed) return
        active = refreshed
      }

      try {
        await fetchUser(active.accessToken)
      } catch (error) {
        if (!isAuthenticationFailure(error)) throw error
        // Access Token 可能被服务端拒绝但 Refresh Token 仍有效，先轮换再重试一次。
        const refreshed = await refreshTokens(true)
        if (!refreshed) throw error
        await fetchUser(refreshed.accessToken)
      }
    } catch (error) {
      // 只有服务端明确判定凭证无效时才清理；断网、超时和 5xx 保留本地会话，
      // 后续定时刷新或用户操作可自动恢复。
      if (isAuthenticationFailure(error)) invalidateSession()
    }
  }, [fetchUser, invalidateSession, refreshTokens])

  useEffect(() => {
    if (restoreStartedRef.current || !tokensRef.current) return
    restoreStartedRef.current = true
    queueMicrotask(() => {
      void restoreSession().finally(() => setIsLoading(false))
    })
  }, [restoreSession])

  // 同步其他标签页的登录、刷新与退出结果。Refresh Token 轮换成功后，所有页面
  // 都使用同一组新令牌；任一页面主动退出后，其余页面立即退出。
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.storageArea !== localStorage) return
      if (event.key === AUTH_TOKENS_STORAGE_KEY || event.key === null) {
        const nextTokens = readStoredTokens()
        tokensRef.current = nextTokens
        setTokens(nextTokens)
        if (!nextTokens) setUser(null)
      }
      if (event.key === AUTH_USER_STORAGE_KEY || event.key === null) {
        setUser(readStoredUser<AuthenticatedUser>())
      }
    }
    window.addEventListener("storage", onStorage)
    return () => window.removeEventListener("storage", onStorage)
  }, [])

  const completeLogin = useCallback(
    async (response: { token: TokenResponse; user: AuthenticatedUser }) => {
      const nextTokens = adoptTokens(toTokens(response.token))
      setUser(response.user)
      persistUser(response.user)
      // 登录接口已经返回可信用户信息。资料刷新失败不应把刚建立的会话清除。
      try {
        await fetchUser(nextTokens.accessToken)
      } catch {
        // 保留登录响应中的用户与令牌，交给后续恢复机制重试。
      }
      return response.user
    },
    [adoptTokens, fetchUser],
  )

  const login = useCallback(
    async (payload: LoginRequest) => {
      await completeLogin(await authService.login(payload))
    },
    [completeLogin],
  )

  const loginWithGoogle = useCallback(
    async (idToken: string) => {
      await completeLogin(await authService.google(idToken))
    },
    [completeLogin],
  )

  const register = useCallback(
    async (payload: RegisterRequest) => {
      const result = await authService.register(payload)
      return completeLogin({ token: result.token, user: result.user as AuthenticatedUser })
    },
    [completeLogin],
  )

  const logout = useCallback(async () => {
    try {
      await withRefreshLock(async () => {
        const current = readStoredTokens() ?? tokensRef.current
        if (current) {
          try {
            await authService.logout({ refreshToken: current.refreshToken })
          } catch {
            // 无论网络状态如何都完成本地退出；接口可达时服务端会撤销令牌。
          }
        }
        clearAll()
      })
    } finally {
      tokensRef.current = null
      setTokens(null)
      setUser(null)
    }
  }, [])

  const refresh = useCallback(async () => {
    const current = tokensRef.current ?? readStoredTokens()
    if (!current || hasUsableAccessToken(current)) return
    try {
      const nextTokens = await refreshTokens()
      if (nextTokens) await fetchUser(nextTokens.accessToken)
    } catch (error) {
      if (isAuthenticationFailure(error)) invalidateSession()
    }
  }, [fetchUser, invalidateSession, refreshTokens])

  const reloadUser = useCallback(async () => {
    const current = tokensRef.current ?? readStoredTokens()
    if (!current) return
    try {
      await fetchUser(current.accessToken)
    } catch (error) {
      if (!isAuthenticationFailure(error)) throw error
      try {
        const nextTokens = await refreshTokens(true)
        if (!nextTokens) throw error
        await fetchUser(nextTokens.accessToken)
      } catch (refreshError) {
        if (isAuthenticationFailure(refreshError)) invalidateSession()
        throw refreshError
      }
    }
  }, [fetchUser, invalidateSession, refreshTokens])

  useEffect(() => {
    if (!tokens) return
    const timer = window.setInterval(() => {
      void refresh()
    }, 60_000)
    return () => window.clearInterval(timer)
  }, [tokens, refresh])

  const value = useMemo<AuthContextValue>(
    () => ({
      user: hydrated ? user : null,
      tokens: hydrated ? tokens : null,
      isLoading: hydrated ? isLoading : true,
      login,
      loginWithGoogle,
      register,
      logout,
      refresh,
      reloadUser,
    }),
    [
      hydrated,
      user,
      tokens,
      isLoading,
      login,
      loginWithGoogle,
      register,
      logout,
      refresh,
      reloadUser,
    ],
  )

  return <ContextProvider value={value}>{children}</ContextProvider>
}
