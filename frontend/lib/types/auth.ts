export type IdentifierType = "PHONE" | "EMAIL" | "USERNAME"

export type VerificationScene = "REGISTER" | "LOGIN" | "RESET_PASSWORD"

export type SendCodeRequest = {
  scene: VerificationScene
  identifierType: IdentifierType
  identifier: string
}

export type SendCodeResponse = {
  identifier: string
  scene: VerificationScene
  expireSeconds: number
}

export type RegisterRequest = {
  identifierType: IdentifierType
  identifier: string
  code?: string
  password?: string
  agreeTerms: boolean
}

export type LoginRequest =
  | {
      identifierType: IdentifierType
      identifier: string
      password: string
      code?: never
    }
  | {
      identifierType: IdentifierType
      identifier: string
      password?: never
      code: string
    }

export type TokenResponse = {
  accessToken: string
  accessTokenExpiresAt: string
  refreshToken: string
  refreshTokenExpiresAt: string
}

export type AuthUserResponse = {
  id: number
  nickname: string
  avatar: string
  phone: string
  email?: string
  lineId?: string
  birthday?: string
  school?: string
  bio?: string
  gender?: Gender
  skills?: string[]
  tagJson?: string
  role?: UserRole
}

export type AuthResponse = {
  user: AuthUserResponse
  token: TokenResponse
}

export type RegisterResponse = AuthResponse

export type LoginResponse = AuthResponse

export type GoogleLoginRequest = {
  idToken: string
}

export type RefreshResponse = TokenResponse

export type LogoutRequest = {
  refreshToken: string
}

export type AuthenticatedUser = AuthUserResponse

export type PublicUserProfile = {
  id: number
  nickname: string
  avatar?: string | null
  phone: string | null
  zhId?: string | null
  birthday?: string | null
  school?: string | null
  bio?: string | null
  gender?: Gender | null
  tagJson?: string | null
  email: string | null
}

export type Gender = "MALE" | "FEMALE" | "OTHER" | "UNKNOWN"

/** 用户角色：USER / ADMIN / SUPER_ADMIN */
export type UserRole = "USER" | "ADMIN" | "SUPER_ADMIN"

/** 注册方式：邮箱+密码（免验证码） / 手机号+验证码 */
export type RegistrationMode = "EMAIL_PASSWORD" | "PHONE_CODE"

/** 公开注册策略（注册页首屏读取） */
export type RegistrationConfig = {
  enabled: boolean
  mode: RegistrationMode
}

export type ErrorResponse = {
  code: string
  message: string
  path?: string
  timestamp?: string
}

export type ResetPasswordRequest = {
  identifierType: IdentifierType
  identifier: string
  code: string
  newPassword: string
}
