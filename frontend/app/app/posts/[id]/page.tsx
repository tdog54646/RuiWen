"use client"

import { useEffect, useRef, useState } from "react"
import Link from "next/link"
import { useParams } from "next/navigation"
import { LikeFavBar } from "@/components/ui/like-fav-bar"
import { FollowButton } from "@/components/ui/follow-button"
import { MarkdownRenderer } from "@/components/ui/markdown-renderer"
import { UserAvatar } from "@/components/ui/user-avatar"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/components/auth/auth-context"
import { knowpostService, withCacheBuster } from "@/lib/api/knowpost"
import { qaService } from "@/lib/api/qa"
import type { KnowpostDetailResponse } from "@/lib/types/knowpost"
import { X, ChevronLeft, ChevronRight, Bot, Send, Loader2 } from "lucide-react"

function isAbortError(error: unknown) {
  return error instanceof Error && error.name === "AbortError"
}

export default function PostDetailPage() {
  const params = useParams<{ id: string }>()
  const id = params.id
  const { tokens, user } = useAuth()
  const [detail, setDetail] = useState<KnowpostDetailResponse | null>(null)
  const [contentText, setContentText] = useState("")
  const [contentError, setContentError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewIndex, setPreviewIndex] = useState(0)

  const [ragQuestion, setRagQuestion] = useState("")
  const [ragAnswer, setRagAnswer] = useState("")
  const [ragLoading, setRagLoading] = useState(false)
  const [ragError, setRagError] = useState<string | null>(null)
  const [hotQuestion, setHotQuestion] = useState<string | null>(null)
  const ragControllerRef = useRef<AbortController | null>(null)

  useEffect(() => {
    let cancelled = false
    if (!id) return
    knowpostService
      .detail(id, tokens?.accessToken ?? undefined)
      .then(async (resp) => {
        if (cancelled) return
        setDetail(resp)
        if (resp.contentUrl) {
          try {
            const text = await fetch(withCacheBuster(resp.contentUrl), {
              credentials: "omit",
            }).then((r) => {
              if (!r.ok) throw new Error(`HTTP ${r.status}`)
              return r.text()
            })
            if (!cancelled) setContentText(text)
          } catch {
            if (!cancelled)
              setContentError("正文暂不可读，可能为非公开或跨域受限")
          }
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "加载失败")
      })
    return () => {
      cancelled = true
    }
  }, [id, tokens?.accessToken])

  useEffect(() => {
    let cancelled = false
    if (!id) {
      setHotQuestion(null)
      return
    }
    knowpostService
      .hotQuestion(id)
      .then((resp) => {
        if (cancelled) return
        const question = resp.question?.trim() ?? ""
        setHotQuestion(question || null)
      })
      .catch(() => {
        if (!cancelled) setHotQuestion(null)
      })
    return () => {
      cancelled = true
    }
  }, [id])

  const streamRag = async (q: string, controller: AbortController) => {
    try {
      await qaService.streamKnowpost(id, {
        question: q,
        topK: 5,
        maxTokens: 1024,
        accessToken: tokens?.accessToken ?? null,
        signal: controller.signal,
        onMessage: (message) => {
          setRagAnswer((prev) => prev + message)
        },
      })
    } catch (err) {
      if (!isAbortError(err)) {
        setRagError(err instanceof Error ? err.message : "请求失败")
      }
    } finally {
      if (ragControllerRef.current === controller) {
        ragControllerRef.current = null
        setRagLoading(false)
      }
    }
  }

  const startRag = (presetQuestion?: string) => {
    if (!id) return
    const questionSource =
      typeof presetQuestion === "string" ? presetQuestion : ragQuestion
    const q = questionSource.trim()
    if (!q) return
    if (!tokens?.accessToken) {
      setRagError("请先登录后提问")
      return
    }
    if (detail && detail.visible !== "public") {
      setRagError("仅公开知文支持问答")
      return
    }
    setRagError(null)
    setRagAnswer("")
    setRagQuestion(q)

    ragControllerRef.current?.abort()
    const controller = new AbortController()
    ragControllerRef.current = controller
    setRagLoading(true)
    void streamRag(q, controller)
  }

  const stopRag = () => {
    ragControllerRef.current?.abort()
    ragControllerRef.current = null
    setRagLoading(false)
  }

  useEffect(() => {
    return () => {
      ragControllerRef.current?.abort()
    }
  }, [])

  const isSelf =
    detail?.authorId &&
    user?.id === detail.authorId

  return (
    <article className="flex flex-col gap-6 rounded-2xl bg-background/90 p-6 shadow-sm">
      <h1 className="text-2xl font-bold tracking-tight">{detail?.title ?? "加载中..."}</h1>

      {error && (
        <div className="text-sm text-destructive">{error}</div>
      )}

      {detail?.images?.length ? (
        <div className="flex gap-3 overflow-x-auto">
          {detail.images.map((src, idx) => (
            <div
              key={idx}
              className="aspect-[3/4] w-44 shrink-0 cursor-pointer overflow-hidden rounded-xl shadow-md"
              onClick={() => {
                setPreviewIndex(idx)
                setPreviewOpen(true)
              }}
            >
              <img
                className="size-full object-cover"
                src={src}
                alt={detail.title}
              />
            </div>
          ))}
        </div>
      ) : null}

      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-3">
          {detail?.authorId ? (
            <Link
              href={`/app/profile/${detail.authorId}`}
              className="flex items-center gap-3 transition-opacity hover:opacity-80"
            >
              {detail.authorAvatar ? (
                <UserAvatar
                  src={detail.authorAvatar}
                  nickname={detail.authorNickname}
                  className="size-9"
                />
              ) : null}
              <span className="font-semibold">{detail.authorNickname}</span>
            </Link>
          ) : (
            <>
              {detail?.authorAvatar ? (
                <UserAvatar
                  src={detail.authorAvatar}
                  nickname={detail.authorNickname}
                  className="size-9"
                />
              ) : null}
              <span className="font-semibold">{detail?.authorNickname}</span>
            </>
          )}
          {detail?.authorId && !isSelf && (
            <FollowButton targetUserId={detail.authorId} />
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          {(detail?.tags ?? []).reduce<string[]>((acc, tag) => acc.includes(tag) ? acc : [...acc, tag], []).map((tag) => (
            <span
              key={tag}
              className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary"
            >
              #{tag}
            </span>
          ))}
        </div>
        {detail?.publishTime && (
          <span className="text-xs text-muted-foreground">
            {new Date(
              Number(detail.publishTime) > 1e15
                ? Number(detail.publishTime) / 1000
                : Number(detail.publishTime)
            ).toLocaleDateString("zh-CN")}
          </span>
        )}
        {detail && (
          <LikeFavBar
            entityId={detail.id}
            initialCounts={{
              like: detail.likeCount ?? 0,
              fav: detail.favoriteCount ?? 0,
            }}
            initialState={{
              liked: detail.liked,
              faved: detail.faved,
            }}
          />
        )}
      </div>

      <div>
        <h2 className="mb-3 text-lg font-semibold">内容正文</h2>
        <div className="rounded-xl bg-muted/30 p-6">
          {contentText ? (
            <MarkdownRenderer content={contentText} />
          ) : (
            <span className="text-muted-foreground">暂无内容</span>
          )}
          {contentError && (
            <div className="mt-2 text-sm text-destructive">
              {contentError}
            </div>
          )}
        </div>

        {/* AI 智能问答 — 移至正文下方 */}
        <div className="mt-6 rounded-xl border p-4">
          <div className="mb-3 flex items-center gap-2 text-sm font-semibold">
            <Bot className="size-4 text-primary" />
            AI 智能问答
          </div>
          {hotQuestion && (
            <div className="mb-3 rounded-lg border bg-muted/20 p-2">
              <p className="text-xs text-muted-foreground">大家都在问：</p>
              <button
                type="button"
                className="mt-1 text-left text-sm text-primary underline-offset-4 hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                onClick={() => startRag(hotQuestion)}
                disabled={ragLoading}
              >
                {hotQuestion}
              </button>
            </div>
          )}
          <textarea
            className="min-h-[60px] w-full resize-y rounded-lg border bg-background p-2 text-sm outline-none focus:border-ring"
            placeholder="围绕本知文提问…"
            value={ragQuestion}
            onChange={(e) => setRagQuestion(e.target.value)}
          />
          <div className="mt-2 flex gap-2">
            <Button
              size="sm"
              onClick={() => startRag()}
              disabled={ragLoading || !ragQuestion.trim()}
            >
              {ragLoading ? (
                <Loader2 className="mr-1 size-3.5 animate-spin" />
              ) : (
                <Send className="mr-1 size-3.5" />
              )}
              {ragLoading ? "生成中…" : "发送"}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={stopRag}
              disabled={!ragLoading}
            >
              停止
            </Button>
          </div>
          <p className="mt-1.5 text-[11px] text-muted-foreground">
            仅&quot;公开&quot;知文支持问答，答案基于当前知文实时生成。
          </p>
          {ragError && (
            <div className="mt-2 text-xs text-destructive">{ragError}</div>
          )}
          <div className="mt-3 flex-1 overflow-auto rounded-lg bg-muted/30 p-3 text-sm">
            {ragAnswer ? (
              <MarkdownRenderer content={ragAnswer} className="prose-sm" />
            ) : (
              <span className="text-muted-foreground">
                {ragLoading ? "等待生成…" : "这里将展示答案（支持流式）"}
              </span>
            )}
          </div>
        </div>
      </div>

      {previewOpen && detail?.images?.length ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
          onClick={() => setPreviewOpen(false)}
        >
          <div
            className="relative max-h-[80vh] max-w-[900px]"
            onClick={(e) => e.stopPropagation()}
          >
            <img
              className="max-h-[80vh] rounded-xl object-contain shadow-xl"
              src={detail.images[previewIndex]}
              alt={detail.title}
            />
            <button
              className="absolute left-2 top-1/2 -translate-y-1/2 rounded-full bg-white/80 p-2 shadow-md"
              onClick={() =>
                setPreviewIndex(
                  (i) =>
                    (i - 1 + detail.images.length) % detail.images.length,
                )
              }
            >
              <ChevronLeft className="size-5" />
            </button>
            <button
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded-full bg-white/80 p-2 shadow-md"
              onClick={() =>
                setPreviewIndex((i) => (i + 1) % detail.images.length)
              }
            >
              <ChevronRight className="size-5" />
            </button>
            <button
              className="absolute right-2 top-2 rounded-full bg-white/80 p-1.5 shadow-md"
              onClick={() => setPreviewOpen(false)}
            >
              <X className="size-4" />
            </button>
          </div>
        </div>
      ) : null}
    </article>
  )
}
