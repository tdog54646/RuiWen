"use client"

import { createContext, useContext } from "react"
import type { AuthenticatedUser, LoginRequest, RegisterRequest } from "@/lib/types/auth"
import type { AuthTokens } from "@/lib/auth/tokens"

export type AuthContextValue = {
  user: AuthenticatedUser | null
  isLoading: boolean
  tokens: AuthTokens | null
  login: (payload: LoginRequest) => Promise<void>
  register: (payload: RegisterRequest) => Promise<AuthenticatedUser>
  logout: () => Promise<void>
  refresh: () => Promise<void>
  reloadUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export const AuthProvider = AuthContext.Provider

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error("useAuth 必须在 AuthProvider 内部使用")
  }
  return context
}
