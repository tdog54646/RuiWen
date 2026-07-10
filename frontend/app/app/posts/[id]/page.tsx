"use client"

import { useEffect, useRef, useState } from "react"
import Link from "next/link"
import { useParams } from "next/navigation"
import { AnimatePresence, motion } from "framer-motion"
import { LikeFavBar } from "@/components/ui/like-fav-bar"
import { FollowButton } from "@/components/ui/follow-button"
import { MarkdownRenderer } from "@/components/ui/markdown-renderer"
import { UserAvatar } from "@/components/ui/user-avatar"
import { Button } from "@/components/ui/button"
import {
  GlassCard,
  MessageBanner,
  PageHeader,
  SectionLabel,
  StatusChip,
  StudioShell,
} from "@/components/ui/studio"
import { useAuth } from "@/components/auth/auth-context"
import { knowpostService, withCacheBuster } from "@/lib/api/knowpost"
import { qaService } from "@/lib/api/qa"
import type { KnowpostDetailResponse } from "@/lib/types/knowpost"
import {
  X,
  ChevronLeft,
  ChevronRight,
  Bot,
  Send,
  Loader2,
  Sparkles,
  Flame,
  FileDown,
} from "lucide-react"
import { toast } from "sonner"

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

  const [exporting, setExporting] = useState(false)

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

  const handleExportPdf = async () => {
    if (!id || !detail) return
    setExporting(true)
    try {
      await knowpostService.exportPdf(id, tokens?.accessToken, detail.title)
      toast.success("已导出 PDF")
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "导出失败")
    } finally {
      setExporting(false)
    }
  }

  useEffect(() => {
    return () => {
      ragControllerRef.current?.abort()
    }
  }, [])

  const isSelf = detail?.authorId && user?.id === detail.authorId

  const uniqueTags = (detail?.tags ?? []).reduce<string[]>(
    (acc, tag) => (acc.includes(tag) ? acc : [...acc, tag]),
    [],
  )

  return (
    <StudioShell>
      <PageHeader
        title={detail?.title ?? "加载中..."}
        subtitle={
          detail?.publishTime
            ? `发布于 ${new Date(
                Number(detail.publishTime) > 1e15
                  ? Number(detail.publishTime) / 1000
                  : Number(detail.publishTime),
              ).toLocaleDateString("zh-CN")}`
            : undefined
        }
        badge={
          <div className="flex flex-wrap items-center gap-1.5">
            {uniqueTags.map((tag) => (
              <span
                key={tag}
                className="rounded-full bg-violet-100/70 px-2 py-0.5 text-[11px] font-medium text-violet-700"
              >
                #{tag}
              </span>
            ))}
            {detail && (
              <StatusChip tone={detail.visible === "public" ? "emerald" : "default"}>
                {detail.visible === "public" ? "公开" : "私密"}
              </StatusChip>
            )}
          </div>
        }
      />

      <MessageBanner tone="error" show={!!error}>
        {error}
      </MessageBanner>

      {/* 作者 + 互动 */}
      <GlassCard delay={0.05} contentClassName="flex flex-wrap items-center justify-between gap-4">
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
                  className="size-10 ring-2 ring-white/60"
                />
              ) : null}
              <span className="font-semibold text-slate-800">
                {detail.authorNickname}
              </span>
            </Link>
          ) : (
            <div className="flex items-center gap-3">
              {detail?.authorAvatar ? (
                <UserAvatar
                  src={detail.authorAvatar}
                  nickname={detail.authorNickname}
                  className="size-10 ring-2 ring-white/60"
                />
              ) : null}
              <span className="font-semibold text-slate-800">
                {detail?.authorNickname}
              </span>
            </div>
          )}
          {detail?.authorId && !isSelf && (
            <FollowButton targetUserId={detail.authorId} />
          )}
        </div>
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
      </GlassCard>

      {/* 图片画廊 */}
      {detail?.images?.length ? (
        <GlassCard delay={0.1} disableHover>
          <SectionLabel>图片</SectionLabel>
          <div className="mt-3 flex gap-3 overflow-x-auto pb-2">
            {detail.images.map((src, idx) => (
              <motion.div
                key={idx}
                whileHover={{ y: -3 }}
                className="aspect-[3/4] w-40 shrink-0 cursor-pointer overflow-hidden rounded-xl ring-1 ring-black/5"
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
              </motion.div>
            ))}
          </div>
        </GlassCard>
      ) : null}

      {/* 正文 */}
      <GlassCard delay={0.15} disableHover>
        <div className="flex items-center justify-between gap-3">
          <SectionLabel>内容正文</SectionLabel>
          <Button
            size="sm"
            variant="outline"
            onClick={handleExportPdf}
            disabled={exporting || !detail}
            className="gap-1.5 border-white/60 bg-white/60 backdrop-blur-md"
          >
            {exporting ? (
              <Loader2 className="size-3.5 animate-spin" />
            ) : (
              <FileDown className="size-3.5" />
            )}
            {exporting ? "导出中…" : "导出 PDF"}
          </Button>
        </div>
        <div className="mt-3">
          {contentText ? (
            <MarkdownRenderer content={contentText} />
          ) : (
            <span className="text-sm text-slate-400">暂无内容</span>
          )}
          <MessageBanner tone="error" show={!!contentError}>
            {contentError}
          </MessageBanner>
        </div>
      </GlassCard>

      {/* AI 智能问答 */}
      <GlassCard delay={0.2} disableHover contentClassName="flex flex-col gap-4">
        <div className="flex items-center gap-2">
          <div className="flex size-8 items-center justify-center rounded-lg bg-gradient-to-br from-cyan-400/20 to-violet-500/20 text-violet-600">
            <Bot className="size-4" />
          </div>
          <SectionLabel>AI 智能问答</SectionLabel>
        </div>

        {hotQuestion && (
          <button
            type="button"
            className="flex items-center gap-2 rounded-xl border border-white/60 bg-white/40 px-3 py-2.5 text-left backdrop-blur-md transition-colors hover:bg-white/60 disabled:cursor-not-allowed disabled:opacity-50"
            onClick={() => startRag(hotQuestion)}
            disabled={ragLoading}
          >
            <Flame className="size-4 shrink-0 text-orange-500" />
            <span className="flex flex-col">
              <span className="text-[11px] text-slate-400">大家都在问</span>
              <span className="text-sm font-medium text-slate-700">
                {hotQuestion}
              </span>
            </span>
          </button>
        )}

        <textarea
          className="min-h-[70px] w-full resize-y rounded-xl border border-white/60 bg-white/50 p-3 text-sm outline-none transition-colors placeholder:text-slate-400 focus:border-cyan-400/60 focus:bg-white/70"
          placeholder="围绕本知文提问…"
          value={ragQuestion}
          onChange={(e) => setRagQuestion(e.target.value)}
        />

        <div className="flex flex-wrap gap-2">
          <Button
            size="sm"
            onClick={() => startRag()}
            disabled={ragLoading || !ragQuestion.trim()}
            className="gap-1.5 bg-gradient-to-r from-cyan-500 to-violet-600 text-white"
          >
            {ragLoading ? (
              <Loader2 className="size-3.5 animate-spin" />
            ) : (
              <Send className="size-3.5" />
            )}
            {ragLoading ? "生成中…" : "发送"}
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={stopRag}
            disabled={!ragLoading}
            className="border-white/60 bg-white/60 backdrop-blur-md"
          >
            停止
          </Button>
        </div>

        <p className="flex items-center gap-1 text-[11px] text-slate-400">
          <Sparkles className="size-3" />
          仅&quot;公开&quot;知文支持问答，答案基于当前知文实时生成。
        </p>

        <MessageBanner tone="error" show={!!ragError}>
          {ragError}
        </MessageBanner>

        {(ragAnswer || ragLoading) && (
          <div className="flex-1 overflow-auto rounded-xl border border-white/60 bg-white/40 p-4 text-sm backdrop-blur-md">
            {ragAnswer ? (
              <MarkdownRenderer content={ragAnswer} className="prose-sm" />
            ) : (
              <span className="text-slate-400">等待生成…</span>
            )}
          </div>
        )}
      </GlassCard>

      {/* 图片预览灯箱 */}
      <AnimatePresence>
        {previewOpen && detail?.images?.length ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-md"
            onClick={() => setPreviewOpen(false)}
          >
            <div
              className="relative max-h-[80vh] max-w-[900px]"
              onClick={(e) => e.stopPropagation()}
            >
              <motion.img
                key={previewIndex}
                initial={{ scale: 0.94, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ type: "spring", stiffness: 300, damping: 26 }}
                className="max-h-[80vh] rounded-2xl object-contain shadow-2xl"
                src={detail.images[previewIndex]}
                alt={detail.title}
              />
              <button
                className="absolute left-2 top-1/2 -translate-y-1/2 rounded-full border border-white/60 bg-white/80 p-2 shadow-md backdrop-blur-md transition-colors hover:bg-white"
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
                className="absolute right-2 top-1/2 -translate-y-1/2 rounded-full border border-white/60 bg-white/80 p-2 shadow-md backdrop-blur-md transition-colors hover:bg-white"
                onClick={() =>
                  setPreviewIndex((i) => (i + 1) % detail.images.length)
                }
              >
                <ChevronRight className="size-5" />
              </button>
              <button
                className="absolute right-2 top-2 rounded-full border border-white/60 bg-white/80 p-1.5 shadow-md backdrop-blur-md transition-colors hover:bg-white"
                onClick={() => setPreviewOpen(false)}
              >
                <X className="size-4" />
              </button>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </StudioShell>
  )
}
