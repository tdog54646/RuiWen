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
  code: string
  password: string
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
}

export type AuthResponse = {
  user: AuthUserResponse
  token: TokenResponse
}

export type RegisterResponse = AuthResponse

export type LoginResponse = AuthResponse

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
