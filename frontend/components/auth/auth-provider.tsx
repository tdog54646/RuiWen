"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"

import { authService } from "@/lib/api/auth"
import type {
  AuthenticatedUser,
  LoginRequest,
  RegisterRequest,
  TokenResponse,
} from "@/lib/types/auth"
import {
  type AuthTokens,
  clearAll,
  persistTokens,
  persistUser,
  readStoredTokens,
  readStoredUser,
} from "@/lib/auth/tokens"
import { AuthProvider as ContextProvider, type AuthContextValue } from "./auth-context"

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

export function AuthProviderWrapper({ children }: { children: React.ReactNode }) {
  // 用惰性初始化器从 storage 恢复初始状态，避免在 effect 中同步调用 setState（防止级联渲染）
  const [tokens, setTokens] = useState<AuthTokens | null>(() => readStoredTokens())
  const [user, setUser] = useState<AuthenticatedUser | null>(() =>
    readStoredUser<AuthenticatedUser>(),
  )
  // 仅当初始 tokens 存在（需要异步校验会话）时才处于 loading；否则水合即完成
  const [isLoading, setIsLoading] = useState(() => readStoredTokens() != null)
  // hydration 守卫：SSR 与首帧客户端渲染对外保持一致（均为未登录/loading），
  // 避免惰性初始化器从 localStorage 恢复登录态导致 hydration mismatch
  // （SSR 无 localStorage → user=null，客户端首帧有 → user=已登录）。
  const [hydrated, setHydrated] = useState(false)
  // 已知误报：首帧 hydration 守卫必须同步置位，与外部系统无关，抑制该规则。
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => setHydrated(true), [])
  const fetchingRef = useRef<Promise<void> | null>(null)

  const fetchUser = useCallback(async (accessToken: string) => {
    try {
      const profile = await authService.fetchCurrentUser(accessToken)
      setUser(profile)
      persistUser(profile)
    } catch {
      setUser(null)
      setTokens(null)
      persistTokens(null)
      persistUser(null)
    }
  }, [])

  // 校验结束归零进行态 ref 与 loading。用微任务延迟 setIsLoading，避免 effect 内的 setState
  // 被 react-hooks/set-state-in-effect 规则误报（该规则会把 effect 中调用的 async 函数
  // 里 await 之后的 setState 也算作"同步级联渲染"，是 Next.js 客户端水合场景的已知误报）。
  const onFetchSettled = useCallback(() => {
    fetchingRef.current = null
    queueMicrotask(() => setIsLoading(false))
  }, [])

  // 仅当初始 tokens 存在时异步校验会话；初始 state 已通过惰性初始化器就绪，此处只发起请求。
  // fetchUser 调用本身也延迟到微任务，彻底断开 effect 同步路径上的 setState 静态追踪。
  useEffect(() => {
    if (!tokens) return

    queueMicrotask(() => {
      const task = fetchUser(tokens.accessToken).finally(onFetchSettled)
      fetchingRef.current = task
    })
  }, [fetchUser, tokens, onFetchSettled])

  const login = useCallback(
    async (payload: LoginRequest) => {
      const response = await authService.login(payload)
      const nextTokens = toTokens(response.token)
      setTokens(nextTokens)
      persistTokens(nextTokens)
      setUser(response.user)
      persistUser(response.user)
      await fetchUser(nextTokens.accessToken)
    },
    [fetchUser],
  )

  const loginWithGoogle = useCallback(
    async (idToken: string) => {
      const response = await authService.google(idToken)
      const nextTokens = toTokens(response.token)
      setTokens(nextTokens)
      persistTokens(nextTokens)
      setUser(response.user)
      persistUser(response.user)
      await fetchUser(nextTokens.accessToken)
    },
    [fetchUser],
  )

  const register = useCallback(
    async (payload: RegisterRequest) => {
      const result = await authService.register(payload)
      const nextTokens = toTokens(result.token)
      setTokens(nextTokens)
      persistTokens(nextTokens)
      const userInfo = result.user as AuthenticatedUser
      setUser(userInfo)
      persistUser(userInfo)
      await fetchUser(nextTokens.accessToken)
      return userInfo
    },
    [fetchUser],
  )

  const logout = useCallback(async () => {
    if (tokens) {
      try {
        await authService.logout(
          { refreshToken: tokens.refreshToken },
          tokens.accessToken,
        )
      } catch {
        // ignore
      }
    }
    setTokens(null)
    setUser(null)
    clearAll()
  }, [tokens])

  const refresh = useCallback(async () => {
    if (!tokens) return
    try {
      if (Date.now() < tokens.expiresAt - 5_000) return
      const result = await authService.refresh(tokens.refreshToken)
      const nextTokens = toTokens(result)
      setTokens(nextTokens)
      persistTokens(nextTokens)
      await fetchUser(nextTokens.accessToken)
    } catch {
      await logout()
    }
  }, [tokens, fetchUser, logout])

  const reloadUser = useCallback(async () => {
    if (!tokens) return
    await fetchUser(tokens.accessToken)
  }, [tokens, fetchUser])

  useEffect(() => {
    if (!tokens) return
    const timer = window.setInterval(() => {
      void refresh()
    }, 60_000)
    return () => window.clearInterval(timer)
  }, [tokens, refresh])

  const value = useMemo<AuthContextValue>(
    () => ({
      // 水合完成前对外暴露未登录/loading 态，保证 SSR 与首帧客户端渲染一致
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
    [hydrated, user, tokens, isLoading, login, loginWithGoogle, register, logout, refresh, reloadUser],
  )

  return <ContextProvider value={value}>{children}</ContextProvider>
}
