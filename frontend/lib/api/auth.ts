import { apiFetch } from "./client"
import type {
  AuthenticatedUser,
  LoginRequest,
  LoginResponse,
  LogoutRequest,
  PublicUserProfile,
  RefreshResponse,
  RegisterRequest,
  RegisterResponse,
  ResetPasswordRequest,
  SendCodeRequest,
  SendCodeResponse,
} from "@/lib/types/auth"

const AUTH_PREFIX = "/api/auth"

export const authService = {
  sendCode: (payload: SendCodeRequest) =>
    apiFetch<SendCodeResponse>(`${AUTH_PREFIX}/send-code`, {
      method: "POST",
      body: payload,
    }),

  register: (payload: RegisterRequest) =>
    apiFetch<RegisterResponse>(`${AUTH_PREFIX}/register`, {
      method: "POST",
      body: payload,
    }),

  login: (payload: LoginRequest) =>
    apiFetch<LoginResponse>(`${AUTH_PREFIX}/login`, {
      method: "POST",
      body: payload,
    }),

  logout: (payload: LogoutRequest, accessToken: string) =>
    apiFetch<void>(`${AUTH_PREFIX}/logout`, {
      method: "POST",
      body: payload,
      accessToken,
    }),

  fetchCurrentUser: (accessToken: string) =>
    apiFetch<AuthenticatedUser>(`${AUTH_PREFIX}/me`, {
      accessToken,
    }),

  getUserById: (userId: number) =>
    apiFetch<PublicUserProfile>(`${AUTH_PREFIX}/user?userId=${userId}`),

  refresh: (refreshToken: string) =>
    apiFetch<RefreshResponse>(`${AUTH_PREFIX}/token/refresh`, {
      method: "POST",
      body: { refreshToken },
      accessToken: null,
    }),

  resetPassword: (payload: ResetPasswordRequest) =>
    apiFetch<void>(`${AUTH_PREFIX}/password/reset`, {
      method: "POST",
      body: payload,
      accessToken: null,
    }),
}
