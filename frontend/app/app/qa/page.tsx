"use client"

import { useState, useRef, useEffect } from "react"
import Link from "next/link"
import { useAuth } from "@/components/auth/auth-context"
import { Button } from "@/components/ui/button"
import { Send, Loader2 } from "lucide-react"
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
    <div className="flex flex-col gap-6 rounded-2xl bg-background/90 p-6 shadow-sm">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight">AI问答</h1>
        <p className="text-sm text-muted-foreground">
          基于知识库的智能问答系统
        </p>
      </div>

      {!user && (
        <div className="rounded-xl border bg-muted/40 p-4">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">登录后使用AI问答功能</p>
            <Link href="/login?next=/app/qa">
              <Button size="sm">去登录</Button>
            </Link>
          </div>
        </div>
      )}

      {user && (
        <>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="rounded-xl border bg-background p-4">
              <textarea
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                placeholder="请输入您的问题..."
                className="w-full resize-none border-0 bg-transparent text-sm outline-none focus:ring-0"
                rows={4}
                disabled={isStreaming}
              />
            </div>

            <div className="flex justify-end gap-2">
              <Button
                type="submit"
                disabled={isStreaming || !question.trim()}
                className="gap-2"
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
              >
                停止
              </Button>
            </div>
          </form>

          {error && (
            <div className="rounded-xl border border-destructive/50 bg-destructive/10 p-4">
              <p className="text-sm text-destructive">{error}</p>
            </div>
          )}

          {(answer || isStreaming) && (
            <div className="rounded-xl border bg-background p-6">
              <h2 className="mb-3 text-lg font-semibold">回答</h2>
              <div className="prose prose-sm max-w-none">
                {answer ? (
                  <MarkdownRenderer content={answer} className="prose-sm" />
                ) : (
                  <span className="text-sm text-muted-foreground">
                    {isStreaming ? "等待生成…" : ""}
                  </span>
                )}
              </div>
              <div ref={answerEndRef} />
            </div>
          )}
        </>
      )}
    </div>
  )
}
