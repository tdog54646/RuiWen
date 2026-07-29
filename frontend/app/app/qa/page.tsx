"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import Link from "next/link"
import { Brain, Plus, Sparkles } from "lucide-react"
import { useAuth } from "@/components/auth/auth-context"
import { Button } from "@/components/ui/button"
import { MessageBanner, StudioShell } from "@/components/ui/studio"
import { ChatInput } from "@/components/qa/chat-input"
import { ChatThread } from "@/components/qa/chat-thread"
import { ConversationList } from "@/components/qa/conversation-list"
import { MemoryPanel } from "@/components/qa/memory-panel"
import { qaChatService } from "@/lib/api/qa-chat"
import { uploadChatImage } from "@/lib/api/storage"
import type { Conversation, DraftPayload, QaMessage, SourceArticle } from "@/lib/types"

const SUGGESTIONS = [
  "Line 的知识库能回答哪些问题？",
  "帮我梳理多轮问答的架构设计",
  "什么是 RAG 检索增强生成？",
]

function isAbortError(e: unknown) {
  return e instanceof Error && e.name === "AbortError"
}

export default function QAPage() {
  const { user } = useAuth()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [convLoading, setConvLoading] = useState(false)
  const [activeId, setActiveId] = useState<string | null>(null)
  const [messages, setMessages] = useState<QaMessage[]>([])
  const [streamingContent, setStreamingContent] = useState("")
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [memOpen, setMemOpen] = useState(false)
  const [scope, setScope] = useState<"all" | "private">("all")

  const abortRef = useRef<AbortController | null>(null)
  const streamBuf = useRef("")
  const activeIdRef = useRef<string | null>(null)
  // 当轮命中的知识库来源，流结束后挂到 assistant 消息
  const pendingSourcesRef = useRef<SourceArticle[] | undefined>(undefined)
  // 当轮 AI 生成的文章草稿，流结束后挂到 assistant 消息
  const pendingDraftRef = useRef<DraftPayload | undefined>(undefined)

  const refreshConversations = useCallback(async () => {
    if (!user) return
    setConvLoading(true)
    try {
      setConversations(await qaChatService.listConversations())
    } catch {
      // 忽略列表加载错误
    } finally {
      setConvLoading(false)
    }
  }, [user])

  useEffect(() => {
    refreshConversations()
  }, [refreshConversations])

  // 卸载时中断进行中的流
  useEffect(() => () => abortRef.current?.abort(), [])

  const selectConversation = useCallback(async (id: string) => {
    abortRef.current?.abort()
    activeIdRef.current = id
    setActiveId(id)
    streamBuf.current = ""
    setStreamingContent("")
    setError(null)
    try {
      setMessages(await qaChatService.listMessages(id))
    } catch {
      setMessages([])
    }
  }, [])

  const createConversation = useCallback(async () => {
    try {
      const c = await qaChatService.createConversation()
      setConversations((prev) => [c, ...prev])
      activeIdRef.current = c.id
      setActiveId(c.id)
      setMessages([])
      setStreamingContent("")
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : "创建会话失败")
    }
  }, [])

  const renameConversation = useCallback(async (id: string, title: string) => {
    try {
      const c = await qaChatService.renameConversation(id, title)
      setConversations((prev) => prev.map((x) => (x.id === id ? c : x)))
    } catch {
      // 忽略重命名错误
    }
  }, [])

  const deleteConversation = useCallback(async (id: string) => {
    try {
      await qaChatService.deleteConversation(id)
      setConversations((prev) => prev.filter((x) => x.id !== id))
      if (activeIdRef.current === id) {
        activeIdRef.current = null
        setActiveId(null)
        setMessages([])
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "删除失败")
    }
  }, [])

  const finalizeAssistant = useCallback(() => {
    const text = streamBuf.current
    const sources = pendingSourcesRef.current
    const draft = pendingDraftRef.current
    streamBuf.current = ""
    pendingSourcesRef.current = undefined
    pendingDraftRef.current = undefined
    setStreamingContent("")
    if (text.trim()) {
      setMessages((prev) => [
        ...prev,
        {
          id: `assistant-${Date.now()}`,
          role: "assistant",
          content: text,
          status: "completed",
          createdAt: new Date().toISOString(),
          sources,
          draft,
        },
      ])
    }
  }, [])

  const send = useCallback(
    async (question: string, imageUrls?: string[]) => {
      if (!user) {
        setError("请先登录")
        return
      }
      abortRef.current?.abort()

      // 乐观加入用户消息
      setMessages((prev) => [
        ...prev,
        {
          id: `user-${Date.now()}`,
          role: "user",
          content: question,
          status: "completed",
          createdAt: new Date().toISOString(),
          imageUrls,
        },
      ])
      streamBuf.current = ""
      pendingSourcesRef.current = undefined
      pendingDraftRef.current = undefined
      setStreamingContent("")
      setError(null)
      setIsStreaming(true)

      const controller = new AbortController()
      abortRef.current = controller

      try {
        await qaChatService.streamChat({
          question,
          imageUrls,
          conversationId: activeIdRef.current ?? undefined,
          scope,
          signal: controller.signal,
          onEvent: (evt) => {
            if (evt.type === "meta") {
              // 新建会话场景：后端回传 conversationId
              if (evt.conversationId && !activeIdRef.current) {
                activeIdRef.current = evt.conversationId
                setActiveId(evt.conversationId)
                refreshConversations()
              }
            } else if (evt.type === "delta") {
              streamBuf.current += evt.content
              setStreamingContent(streamBuf.current)
            } else if (evt.type === "sources") {
              pendingSourcesRef.current = evt.items
            } else if (evt.type === "draft") {
              pendingDraftRef.current = {
                postId: evt.postId,
                title: evt.title,
                tags: evt.tags ?? [],
                preview: evt.preview,
              }
            } else if (evt.type === "error") {
              setError(evt.message)
            }
            // done 事件：await 结束后统一 finalize
          },
        })
        finalizeAssistant()
      } catch (e) {
        if (isAbortError(e)) {
          finalizeAssistant() // 中断也保留已生成内容
        } else {
          setError(e instanceof Error ? e.message : "请求失败")
          finalizeAssistant()
        }
      } finally {
        if (abortRef.current === controller) {
          abortRef.current = null
          setIsStreaming(false)
        }
      }
    },
    [user, scope, finalizeAssistant, refreshConversations],
  )

  const stop = useCallback(() => {
    abortRef.current?.abort()
  }, [])

  // 图片上传到 OSS（presign -> PUT -> 公网 URL），URL 随提问一起发给后端供 recognize_image 工具使用
  const handleUploadImage = useCallback(async (file: File): Promise<string> => {
    return uploadChatImage(file)
  }, [])

  if (!user) {
    return (
      <StudioShell centered>
        <section className="w-full max-w-xl rounded-2xl bg-[#fbfbf8] px-8 py-14 text-center shadow-[0_20px_50px_-42px_rgba(29,33,31,0.55)] ring-1 ring-[#deded8]">
          <div className="flex flex-col items-center gap-5">
            <Sparkles className="size-7 text-[#2f5d50]" strokeWidth={1.5} />
            <div>
              <h2 className="font-display text-4xl font-medium tracking-[-0.045em] text-[#1d211f]">
                登录后开启 AI 问答
              </h2>
              <p className="mt-3 text-sm leading-6 text-[#747873]">
                通过多轮追问和个人记忆，从知识库中获得有出处的回答。
              </p>
            </div>
            <Link href="/login?next=/app/qa">
              <Button className="rounded-lg bg-[#1d211f] px-6 hover:bg-[#2f5d50]">去登录</Button>
            </Link>
          </div>
        </section>
      </StudioShell>
    )
  }

  return (
    <StudioShell>
      <MessageBanner tone="error" show={!!error}>
        {error}
      </MessageBanner>

      <div className="flex h-[calc(100dvh-8rem)] overflow-hidden rounded-2xl bg-[#fbfbf8] shadow-[0_24px_60px_-45px_rgba(29,33,31,0.6)] ring-1 ring-[#deded8]">
        {/* 左：会话列表（桌面端） */}
        <aside className="hidden w-72 shrink-0 bg-[#efefe9] md:block">
            <ConversationList
              conversations={conversations}
              activeId={activeId}
              loading={convLoading}
              onSelect={selectConversation}
              onCreate={createConversation}
              onRename={renameConversation}
              onDelete={deleteConversation}
            />
        </aside>

        {/* 右：聊天主区 */}
        <section className="flex h-full min-h-0 flex-1 flex-col overflow-hidden">
          {/* 顶部工具栏 */}
          <div className="flex shrink-0 items-center justify-between gap-3 bg-[#fbfbf8] px-4 py-3.5 md:px-6">
            <div className="flex items-center gap-2.5">
              <Sparkles className="size-5 text-[#2f5d50]" strokeWidth={1.6} />
              <div className="leading-tight">
                <div className="font-display text-lg font-medium tracking-[-0.025em] text-[#252a27]">AI 问答</div>
                <div className="text-sm text-[#777b76]">知识库与多轮记忆</div>
              </div>
            </div>
            <div className="flex items-center gap-1.5">
              <Button
                variant={scope === "private" ? "default" : "outline"}
                size="sm"
                onClick={() => setScope((s) => (s === "private" ? "all" : "private"))}
                className="gap-1 rounded-md border-[#cfd1ca] bg-transparent text-[#4f5550] hover:bg-[#ecece6]"
                title={scope === "private" ? "当前：仅我的私有库，点击切到全部知识" : "当前：全部知识，点击切到仅我的私有库"}
              >
                <Sparkles className="size-3.5" />
                <span className="hidden sm:inline">{scope === "private" ? "仅私有库" : "全部知识"}</span>
                <span className="sm:hidden">{scope === "private" ? "私有" : "全部"}</span>
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={createConversation}
                className="gap-1 rounded-md text-[#4f5550] md:hidden"
              >
                <Plus className="size-4" />
                新建
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setMemOpen(true)}
                className="gap-1 rounded-md border-[#cfd1ca] bg-transparent text-[#4f5550] hover:bg-[#ecece6]"
              >
                <Brain className="size-3.5" />
                <span className="hidden sm:inline">用户记忆</span>
                <span className="sm:hidden">记忆</span>
              </Button>
            </div>
          </div>

          {/* 消息区（占满剩余空间，内部滚动） */}
          <ChatThread
            className="min-h-0 flex-1"
            messages={messages}
            streamingContent={streamingContent}
            isStreaming={isStreaming}
            suggestions={SUGGESTIONS}
            onSuggestion={send}
          />

          {/* 输入区 */}
          <ChatInput
            onSend={send}
            onStop={stop}
            isStreaming={isStreaming}
            onUploadImage={handleUploadImage}
          />
        </section>
      </div>

      <MemoryPanel open={memOpen} onOpenChange={setMemOpen} />
    </StudioShell>
  )
}
