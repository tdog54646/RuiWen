import { apiFetch, apiFetchResponse } from "./client"
import { parseSseStream } from "./sse"
import type {
  Conversation,
  MemoryEntry,
  MemoryCreateInput,
  MemoryUpdateInput,
  QaMessage,
  QaStreamEvent,
} from "../types/qa"

const QA_PREFIX = "/api/qa"

export type StreamChatParams = {
  question: string
  conversationId?: string
  topK?: number
  maxTokens?: number
  signal?: AbortSignal
  onEvent: (event: QaStreamEvent) => void
}

/**
 * 多轮问答 API 服务：会话管理 + 流式问答 + 用户记忆 CRUD。
 * 鉴权 token 与 Content-Type 由 apiFetch 自动处理。
 */
export const qaChatService = {
  // ---- 会话管理 ----
  listConversations: (limit = 50, offset = 0) =>
    apiFetch<Conversation[]>(
      `${QA_PREFIX}/conversations?limit=${limit}&offset=${offset}`,
    ),

  createConversation: (title?: string) =>
    apiFetch<Conversation>(`${QA_PREFIX}/conversations`, {
      method: "POST",
      body: { title },
    }),

  listMessages: (conversationId: string) =>
    apiFetch<QaMessage[]>(
      `${QA_PREFIX}/conversations/${conversationId}/messages`,
    ),

  renameConversation: (conversationId: string, title: string) =>
    apiFetch<Conversation>(`${QA_PREFIX}/conversations/${conversationId}`, {
      method: "PATCH",
      body: { title },
    }),

  deleteConversation: (conversationId: string) =>
    apiFetch<void>(`${QA_PREFIX}/conversations/${conversationId}`, {
      method: "DELETE",
    }),

  // ---- 流式问答 ----
  streamChat: async ({
    question,
    conversationId,
    topK,
    maxTokens,
    signal,
    onEvent,
  }: StreamChatParams) => {
    const response = await apiFetchResponse(`${QA_PREFIX}/chat`, {
      method: "POST",
      headers: { Accept: "text/event-stream" },
      body: { question, conversationId, topK, maxTokens },
      signal,
    })

    await parseSseStream(response, (data) => {
      try {
        onEvent(JSON.parse(data) as QaStreamEvent)
      } catch {
        // 忽略非 JSON 事件
      }
    })
  },

  // ---- 用户记忆 ----
  listMemories: () => apiFetch<MemoryEntry[]>(`${QA_PREFIX}/memories`),

  createMemory: (input: MemoryCreateInput) =>
    apiFetch<MemoryEntry>(`${QA_PREFIX}/memories`, {
      method: "POST",
      body: input,
    }),

  updateMemory: (id: string, input: MemoryUpdateInput) =>
    apiFetch<MemoryEntry>(`${QA_PREFIX}/memories/${id}`, {
      method: "PATCH",
      body: input,
    }),

  deleteMemory: (id: string) =>
    apiFetch<void>(`${QA_PREFIX}/memories/${id}`, { method: "DELETE" }),

  regenerateMemories: () =>
    apiFetch<MemoryEntry[]>(`${QA_PREFIX}/memories/regenerate`, {
      method: "POST",
    }),
}
