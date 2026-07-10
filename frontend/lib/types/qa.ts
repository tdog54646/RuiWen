// AI 多轮问答相关类型（会话 / 消息 / 记忆 / SSE 事件）

export type Conversation = {
  id: string
  title: string
  messageCount: number
  lastMessageAt: string | null
  createdAt: string
}

export type MessageRole = "user" | "assistant"

export type MessageStatus = "streaming" | "completed" | "interrupted" | "error"

export type QaMessage = {
  id: string
  role: MessageRole
  content: string
  status: MessageStatus
  createdAt: string
}

export type MemorySource = "auto" | "manual"

export type MemoryEntry = {
  id: string
  category: string
  content: string
  source: MemorySource
  enabled: boolean
  createdAt: string
  updatedAt: string
}

// ---- SSE 流式事件 ----
export type QaStreamMetaEvent = {
  type: "meta"
  conversationId: string
  userMessageId: string
  assistantMessageId: string
}

export type QaStreamDeltaEvent = {
  type: "delta"
  content: string
}

export type QaStreamDoneEvent = { type: "done" }

export type QaStreamErrorEvent = {
  type: "error"
  message: string
}

export type QaStreamEvent =
  | QaStreamMetaEvent
  | QaStreamDeltaEvent
  | QaStreamDoneEvent
  | QaStreamErrorEvent

// ---- 请求 DTO ----
export type QaChatRequest = {
  conversationId?: string
  question: string
  topK?: number
  maxTokens?: number
}

export type MemoryCreateInput = {
  category?: string
  content: string
}

export type MemoryUpdateInput = {
  category?: string
  content?: string
  enabled?: boolean
}
