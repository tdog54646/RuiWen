"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import Link from "next/link"
import { Brain, Plus, Sparkles } from "lucide-react"
import { useAuth } from "@/components/auth/auth-context"
import { Button } from "@/components/ui/button"
import { GlassCard, MessageBanner, StudioShell } from "@/components/ui/studio"
import { ChatInput } from "@/components/qa/chat-input"
import { ChatThread } from "@/components/qa/chat-thread"
import { ConversationList } from "@/components/qa/conversation-list"
import { MemoryPanel } from "@/components/qa/memory-panel"
import { qaChatService } from "@/lib/api/qa-chat"
import type { Conversation, QaMessage } from "@/lib/types"

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
    streamBuf.current = ""
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
        },
      ])
    }
  }, [])

  const send = useCallback(
    async (question: string) => {
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
        },
      ])
      streamBuf.current = ""
      setStreamingContent("")
      setError(null)
      setIsStreaming(true)

      const controller = new AbortController()
      abortRef.current = controller

      try {
        await qaChatService.streamChat({
          question,
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

  if (!user) {
    return (
      <StudioShell centered>
        <GlassCard className="max-w-md">
          <div className="flex flex-col items-center gap-4 text-center">
            <div className="flex size-14 items-center justify-center rounded-2xl bg-gradient-to-br from-cyan-400/20 to-violet-500/20 ring-1 ring-white/50">
              <Sparkles className="size-6 text-violet-500" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-slate-800">
                登录后开启 AI 问答
              </h2>
              <p className="mt-1 text-sm text-slate-400">
                多轮追问、用户记忆，知识库随身问答
              </p>
            </div>
            <Link href="/login?next=/app/qa">
              <Button>去登录</Button>
            </Link>
          </div>
        </GlassCard>
      </StudioShell>
    )
  }

  return (
    <StudioShell>
      <MessageBanner tone="error" show={!!error}>
        {error}
      </MessageBanner>

      <div className="flex h-[calc(100dvh-7.5rem)] gap-4">
        {/* 左：会话列表（桌面端） */}
        <aside className="hidden w-64 shrink-0 md:block">
          <GlassCard className="h-full overflow-hidden p-0" contentClassName="h-full">
            <ConversationList
              conversations={conversations}
              activeId={activeId}
              loading={convLoading}
              onSelect={selectConversation}
              onCreate={createConversation}
              onRename={renameConversation}
              onDelete={deleteConversation}
            />
          </GlassCard>
        </aside>

        {/* 右：聊天主区 */}
        <GlassCard
          className="h-full min-h-0 flex-1 overflow-hidden p-0"
          contentClassName="flex h-full min-h-0 flex-col"
        >
          {/* 顶部工具栏 */}
          <div className="flex shrink-0 items-center justify-between gap-3 border-b border-white/40 px-4 py-3 md:px-6">
            <div className="flex items-center gap-2.5">
              <div className="flex size-9 items-center justify-center rounded-xl bg-gradient-to-br from-cyan-400/20 to-violet-500/20 ring-1 ring-white/50">
                <Sparkles className="size-4 text-violet-500" />
              </div>
              <div className="leading-tight">
                <div className="text-sm font-semibold text-slate-800">AI 问答</div>
                <div className="text-[11px] text-slate-400">知识库 · 多轮记忆</div>
              </div>
            </div>
            <div className="flex items-center gap-1.5">
              <Button
                variant={scope === "private" ? "default" : "outline"}
                size="sm"
                onClick={() => setScope((s) => (s === "private" ? "all" : "private"))}
                className="gap-1"
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
                className="gap-1 md:hidden"
              >
                <Plus className="size-4" />
                新建
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setMemOpen(true)}
                className="gap-1"
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
          <ChatInput onSend={send} onStop={stop} isStreaming={isStreaming} />
        </GlassCard>
      </div>

      <MemoryPanel open={memOpen} onOpenChange={setMemOpen} />
    </StudioShell>
  )
}
