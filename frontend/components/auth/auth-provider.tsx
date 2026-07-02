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
  const [tokens, setTokens] = useState<AuthTokens | null>(null)
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const fetchingRef = useRef<Promise<void> | null>(null)
  const initializedRef = useRef(false)

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

  useEffect(() => {
    if (initializedRef.current) return
    initializedRef.current = true

    const storedTokens = readStoredTokens()
    const storedUser = readStoredUser<AuthenticatedUser>()

    if (!storedTokens) {
      setIsLoading(false)
      return
    }

    setTokens(storedTokens)
    setUser(storedUser)

    const task = fetchUser(storedTokens.accessToken).finally(() => {
      fetchingRef.current = null
      setIsLoading(false)
    })
    fetchingRef.current = task
  }, [fetchUser])

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
      user,
      tokens,
      isLoading,
      login,
      register,
      logout,
      refresh,
      reloadUser,
    }),
    [user, tokens, isLoading, login, register, logout, refresh, reloadUser],
  )

  return <ContextProvider value={value}>{children}</ContextProvider>
}
