"use client"

import { useState, useRef, useEffect } from "react"
import Link from "next/link"
import { useAuth } from "@/components/auth/auth-context"
import { Button } from "@/components/ui/button"
import {
  GlassCard,
  MessageBanner,
  PageHeader,
  SectionLabel,
  StatusChip,
  StudioShell,
} from "@/components/ui/studio"
import { Send, Loader2, Bot, Sparkles, Square } from "lucide-react"
import { MarkdownRenderer } from "@/components/ui/markdown-renderer"
import { qaService } from "@/lib/api/qa"

function isAbortError(error: unknown) {
  return error instanceof Error && error.name === "AbortError"
}

export default function QAPage() {
  const { user, tokens } = useAuth()
  const [question, setQuestion] = useState("")
  const [answer, setAnswer] = useState("")
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const streamControllerRef = useRef<AbortController | null>(null)
  const answerEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (answer) {
      answerEndRef.current?.scrollIntoView({ behavior: "smooth" })
    }
  }, [answer])

  useEffect(() => {
    return () => {
      streamControllerRef.current?.abort()
    }
  }, [])

  const startStream = async (q: string, accessToken: string) => {
    streamControllerRef.current?.abort()

    const controller = new AbortController()
    streamControllerRef.current = controller
    setError(null)
    setAnswer("")
    setIsStreaming(true)

    try {
      await qaService.streamKnowledgeBase({
        question: q,
        topK: 5,
        maxTokens: 1024,
        accessToken,
        signal: controller.signal,
        onMessage: (message) => {
          setAnswer((prev) => prev + message)
        },
      })
    } catch (err) {
      if (!isAbortError(err)) {
        setError(err instanceof Error ? err.message : "请求失败")
      }
    } finally {
      if (streamControllerRef.current === controller) {
        streamControllerRef.current = null
        setIsStreaming(false)
      }
    }
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()

    const q = question.trim()
    if (!q) {
      setError("请输入问题")
      return
    }

    if (!user || !tokens?.accessToken) {
      setError("请先登录")
      return
    }

    void startStream(q, tokens.accessToken)
  }

  const handleStop = () => {
    streamControllerRef.current?.abort()
    streamControllerRef.current = null
    setIsStreaming(false)
  }

  return (
    <StudioShell>
      <PageHeader
        badge={
          <StatusChip icon={Sparkles} tone="violet">
            知识库问答
          </StatusChip>
        }
        title="AI 问答"
        subtitle="基于知识库的智能问答系统，实时流式生成"
        chips={
          isStreaming ? (
            <StatusChip icon={Loader2} tone="cyan">
              生成中
            </StatusChip>
          ) : null
        }
      />

      {!user && (
        <GlassCard delay={0.05}>
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-slate-500">登录后使用 AI 问答功能</p>
            <Link href="/login?next=/app/qa">
              <Button size="sm">去登录</Button>
            </Link>
          </div>
        </GlassCard>
      )}

      {user && (
        <GlassCard delay={0.05} disableHover>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <SectionLabel>你的问题</SectionLabel>
            <textarea
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              placeholder="请输入您的问题..."
              className="min-h-[110px] w-full resize-none rounded-xl border border-white/60 bg-white/50 p-4 text-sm leading-relaxed outline-none transition-colors placeholder:text-slate-400 focus:border-cyan-400/60 focus:bg-white/70"
              rows={4}
              disabled={isStreaming}
            />
            <div className="flex justify-end gap-2">
              <Button
                type="submit"
                disabled={isStreaming || !question.trim()}
                className="gap-1.5 bg-gradient-to-r from-cyan-500 to-violet-600 text-white"
              >
                {isStreaming ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Send className="size-4" />
                )}
                {isStreaming ? "生成中…" : "发送"}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={handleStop}
                disabled={!isStreaming}
                className="gap-1.5 border-white/60 bg-white/60 backdrop-blur-md"
              >
                <Square className="size-3.5" />
                停止
              </Button>
            </div>
          </form>

          <div className="mt-4">
            <MessageBanner tone="error" show={!!error}>
              {error}
            </MessageBanner>
          </div>
        </GlassCard>
      )}

      {(answer || isStreaming) && (
        <GlassCard delay={0.1} disableHover contentClassName="flex flex-col gap-4">
          <div className="flex items-center gap-2">
            <div className="flex size-8 items-center justify-center rounded-lg bg-gradient-to-br from-cyan-400/20 to-violet-500/20 text-violet-600">
              <Bot className="size-4" />
            </div>
            <SectionLabel>AI 回答</SectionLabel>
          </div>
          <div className="prose prose-sm max-w-none">
            {answer ? (
              <MarkdownRenderer content={answer} className="prose-sm" />
            ) : (
              <span className="text-sm text-slate-400">
                {isStreaming ? "等待生成…" : ""}
              </span>
            )}
          </div>
          <div ref={answerEndRef} />
        </GlassCard>
      )}
    </StudioShell>
  )
}
