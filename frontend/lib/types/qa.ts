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

/** AI 回答命中知识库后下发的来源文章（用于「为您推荐」）。 */
export type SourceArticle = {
  postId: string
  title: string
}

/** AI 录入文章生成的草稿摘要（用于「草稿卡片」）。 */
export type DraftPayload = {
  postId: string
  title: string
  tags: string[]
  preview: string
}

export type QaMessage = {
  id: string
  role: MessageRole
  content: string
  status: MessageStatus
  createdAt: string
  sources?: SourceArticle[]
  draft?: DraftPayload
  /** 用户当轮附带的图片公网 URL 数组（user 消息；落库后历史回看也含此字段）。 */
  imageUrls?: string[]
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

export type QaStreamSourcesEvent = {
  type: "sources"
  items: SourceArticle[]
}

export type QaStreamDraftEvent = {
  type: "draft"
  postId: string
  title: string
  tags: string[]
  preview: string
}

export type QaStreamEvent =
  | QaStreamMetaEvent
  | QaStreamDeltaEvent
  | QaStreamDoneEvent
  | QaStreamSourcesEvent
  | QaStreamDraftEvent
  | QaStreamErrorEvent

// ---- 请求 DTO ----
export type QaChatRequest = {
  conversationId?: string
  question: string
  imageUrls?: string[]
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
