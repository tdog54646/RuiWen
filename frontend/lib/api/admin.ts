import { apiFetch } from "./client"
import type { RegistrationConfig } from "@/lib/types/auth"
import type {
  AdminConversationItem,
  AdminConversationQuery,
  AdminKnowPostItem,
  AdminMemoryItem,
  AdminMemoryQuery,
  AdminMessageItem,
  AdminUserDetail,
  AdminUserListItem,
  DashboardStats,
  IndexStats,
  LoginLog,
  PageResult,
  RebuildStatus,
  SystemSettings,
} from "@/lib/types/admin"

const ADMIN_PREFIX = "/api/admin"

export type AdminUserQuery = {
  keyword?: string
  role?: string
  status?: string
  page?: number
  size?: number
}

export type AdminLoginLogQuery = {
  userId?: number
  identifier?: string
  status?: string
  channel?: string
  start?: string
  end?: string
  page?: number
  size?: number
}

export type AdminKnowPostQuery = {
  keyword?: string
  status?: string
  visible?: string
  creatorId?: number
  page?: number
  size?: number
}

function toSearchParams(query: Record<string, unknown>): string {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.append(key, String(value))
    }
  })
  const str = params.toString()
  return str ? `?${str}` : ""
}

export const adminService = {
  // 仪表盘
  getDashboardStats: (accessToken: string) =>
    apiFetch<DashboardStats>(`${ADMIN_PREFIX}/dashboard/stats`, { accessToken }),

  // 用户管理
  listUsers: (accessToken: string, query: AdminUserQuery) =>
    apiFetch<PageResult<AdminUserListItem>>(
      `${ADMIN_PREFIX}/users${toSearchParams(query)}`,
      { accessToken },
    ),

  getUserDetail: (accessToken: string, id: number) =>
    apiFetch<AdminUserDetail>(`${ADMIN_PREFIX}/users/${id}`, { accessToken }),

  updateUserRole: (accessToken: string, id: number, role: string) =>
    apiFetch<void>(`${ADMIN_PREFIX}/users/${id}/role`, {
      method: "PATCH",
      body: { role },
      accessToken,
    }),

  updateUserStatus: (accessToken: string, id: number, status: string) =>
    apiFetch<void>(`${ADMIN_PREFIX}/users/${id}/status`, {
      method: "PATCH",
      body: { status },
      accessToken,
    }),

  resetUserPassword: (accessToken: string, id: number, newPassword?: string) =>
    apiFetch<{ password: string }>(`${ADMIN_PREFIX}/users/${id}/reset-password`, {
      method: "POST",
      body: newPassword !== undefined ? { newPassword } : {},
      accessToken,
    }),

  // 注册策略
  getRegistration: (accessToken: string) =>
    apiFetch<RegistrationConfig>(`${ADMIN_PREFIX}/registration`, { accessToken }),

  updateRegistration: (accessToken: string, enabled: boolean, mode: string) =>
    apiFetch<RegistrationConfig>(`${ADMIN_PREFIX}/registration`, {
      method: "PUT",
      body: { enabled, mode },
      accessToken,
    }),

  // 登录审计
  listLoginLogs: (accessToken: string, query: AdminLoginLogQuery) =>
    apiFetch<PageResult<LoginLog>>(
      `${ADMIN_PREFIX}/audit/login-logs${toSearchParams(query)}`,
      { accessToken },
    ),

  // 内容审核
  listPosts: (accessToken: string, query: AdminKnowPostQuery) =>
    apiFetch<PageResult<AdminKnowPostItem>>(
      `${ADMIN_PREFIX}/posts${toSearchParams(query)}`,
      { accessToken },
    ),

  updatePostVisibility: (accessToken: string, id: string, visible: string) =>
    apiFetch<void>(`${ADMIN_PREFIX}/posts/${id}/visibility`, {
      method: "PATCH",
      body: { visible },
      accessToken,
    }),

  updatePostTop: (accessToken: string, id: string, isTop: boolean) =>
    apiFetch<void>(`${ADMIN_PREFIX}/posts/${id}/top`, {
      method: "PATCH",
      body: { isTop },
      accessToken,
    }),

  deletePost: (accessToken: string, id: string) =>
    apiFetch<void>(`${ADMIN_PREFIX}/posts/${id}`, {
      method: "DELETE",
      accessToken,
    }),

  // 系统配置
  getSettings: (accessToken: string) =>
    apiFetch<SystemSettings>(`${ADMIN_PREFIX}/settings`, { accessToken }),

  updateSettings: (
    accessToken: string,
    body: { passwordMinLength?: number; announcement?: string },
  ) =>
    apiFetch<void>(`${ADMIN_PREFIX}/settings`, {
      method: "PUT",
      body,
      accessToken,
    }),

  // AI 对话：会话审计
  listConversations: (accessToken: string, query: AdminConversationQuery) =>
    apiFetch<PageResult<AdminConversationItem>>(
      `${ADMIN_PREFIX}/qa/conversations${toSearchParams({ ...query, includeDeleted: query.includeDeleted ? "true" : undefined })}`,
      { accessToken },
    ),

  listMessages: (accessToken: string, conversationId: string, page = 1, size = 50) =>
    apiFetch<PageResult<AdminMessageItem>>(
      `${ADMIN_PREFIX}/qa/conversations/${conversationId}/messages?page=${page}&size=${size}`,
      { accessToken },
    ),

  deleteConversation: (accessToken: string, id: string) =>
    apiFetch<void>(`${ADMIN_PREFIX}/qa/conversations/${id}`, {
      method: "DELETE",
      accessToken,
    }),

  deleteMessage: (accessToken: string, id: string) =>
    apiFetch<void>(`${ADMIN_PREFIX}/qa/messages/${id}`, {
      method: "DELETE",
      accessToken,
    }),

  // AI 对话：用户记忆
  listMemories: (accessToken: string, query: AdminMemoryQuery) =>
    apiFetch<PageResult<AdminMemoryItem>>(
      `${ADMIN_PREFIX}/qa/memories${toSearchParams(query)}`,
      { accessToken },
    ),

  updateMemoryEnabled: (accessToken: string, id: string, enabled: boolean) =>
    apiFetch<void>(`${ADMIN_PREFIX}/qa/memories/${id}/enabled`, {
      method: "PATCH",
      body: { enabled },
      accessToken,
    }),

  deleteMemory: (accessToken: string, id: string) =>
    apiFetch<void>(`${ADMIN_PREFIX}/qa/memories/${id}`, {
      method: "DELETE",
      accessToken,
    }),

  // 索引库管理（RAG 向量索引）
  getIndexStats: (accessToken: string) =>
    apiFetch<IndexStats>(`${ADMIN_PREFIX}/index/rag/stats`, { accessToken }),

  rebuildRagPost: (accessToken: string, id: string) =>
    apiFetch<number>(`${ADMIN_PREFIX}/index/rag/posts/${id}/rebuild`, {
      method: "POST",
      accessToken,
    }),

  deleteRagPostIndex: (accessToken: string, id: string) =>
    apiFetch<void>(`${ADMIN_PREFIX}/index/rag/posts/${id}`, {
      method: "DELETE",
      accessToken,
    }),

  rebuildAllRagIndex: (accessToken: string) =>
    apiFetch<RebuildStatus>(`${ADMIN_PREFIX}/index/rag/rebuild-all`, {
      method: "POST",
      accessToken,
    }),

  getRebuildAllStatus: (accessToken: string) =>
    apiFetch<RebuildStatus>(`${ADMIN_PREFIX}/index/rag/rebuild-all/status`, { accessToken }),
}
