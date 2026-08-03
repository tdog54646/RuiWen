import { apiFetch } from "./client"
import type {
  AuthenticatedUser,
  GoogleLoginRequest,
  LoginRequest,
  LoginResponse,
  LogoutRequest,
  PublicUserProfile,
  RefreshResponse,
  RegistrationConfig,
  RegisterRequest,
  RegisterResponse,
  ResetPasswordRequest,
  SendCodeRequest,
  SendCodeResponse,
} from "@/lib/types/auth"

const AUTH_PREFIX = "/api/auth"

export const authService = {
  getRegistrationConfig: () =>
    apiFetch<RegistrationConfig>(`${AUTH_PREFIX}/registration-config`, {
      accessToken: null,
    }),

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

  google: (idToken: string) =>
    apiFetch<LoginResponse>(`${AUTH_PREFIX}/google`, {
      method: "POST",
      body: { idToken } as GoogleLoginRequest,
    }),

  // 登出只凭 Refresh Token 撤销会话。显式禁用默认的 Access Token 注入，
  // 避免 Access Token 过期时被资源服务器过滤器提前拦截，导致服务端令牌未撤销。
  logout: (payload: LogoutRequest) =>
    apiFetch<void>(`${AUTH_PREFIX}/logout`, {
      method: "POST",
      body: payload,
      accessToken: null,
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
