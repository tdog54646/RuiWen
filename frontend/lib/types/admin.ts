import type { RegistrationMode, UserRole } from "@/lib/types/auth"

/** 通用分页结果。 */
export type PageResult<T> = {
  items: T[]
  total: number
  page: number
  size: number
}

export type UserStatus = "ACTIVE" | "BANNED"

export type AdminUserListItem = {
  id: number
  nickname: string
  phone: string | null
  email: string | null
  role: UserRole
  status: UserStatus
  avatar: string | null
  createdAt: string
}

export type AdminUserDetail = {
  id: number
  nickname: string
  phone: string | null
  email: string | null
  role: UserRole
  status: UserStatus
  avatar: string | null
  bio: string | null
  gender: string | null
  birthday: string | null
  school: string | null
  zgId: string | null
  hasPassword: boolean
  createdAt: string
  updatedAt: string
}

export type DashboardStats = {
  totalUsers: number
  newUsersToday: number
  bannedUsers: number
  totalPosts: number
  publishedPosts: number
  loginsToday: number
  roleDistribution: Record<string, number>
  totalConversations: number
  totalMessages: number
  totalMemories: number
}

export type AdminKnowPostItem = {
  /** 雪花 ID，必须以字符串接收以避免 JS 精度丢失 */
  id: string
  title: string | null
  description: string | null
  creatorId: number
  creatorNickname: string | null
  status: string
  visible: string
  isTop: boolean
  type: string
  tags: string | null
  createTime: string
  publishTime: string | null
}

export type LoginLog = {
  id: number
  userId: number | null
  identifier: string
  channel: string
  ip: string | null
  userAgent: string | null
  status: string
  createdAt: string
}

export type SystemSettings = {
  password: { minLength: number; bcryptStrength: number }
  verification: {
    codeLength: number
    ttl: string
    maxAttempts: number
    sendInterval: string
    dailyLimit: number
  }
  jwt: { accessTokenTtl: string; refreshTokenTtl: string }
  registration: { enabled: boolean; mode: RegistrationMode }
  announcement: string
}

/** AI 会话列表项（ID 为雪花，字符串承载）。 */
export type AdminConversationItem = {
  id: string
  userId: number
  userNickname: string | null
  title: string
  messageCount: number
  lastMessageAt: string | null
  deleted: boolean
  createdAt: string
}

/** AI 消息项（会话详情）。 */
export type AdminMessageItem = {
  id: string
  conversationId: number
  userId: number
  role: string
  content: string
  status: string
  createdAt: string
}

/** 用户记忆（AI 记忆）列表项。 */
export type AdminMemoryItem = {
  id: string
  userId: number
  userNickname: string | null
  category: string
  content: string
  source: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export type AdminConversationQuery = {
  keyword?: string
  userId?: number
  includeDeleted?: boolean
  page?: number
  size?: number
}

export type AdminMemoryQuery = {
  keyword?: string
  userId?: number
  source?: string
  page?: number
  size?: number
}
