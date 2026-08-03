"use client"

import { useEffect, useRef } from "react"
import Link from "next/link"
import { motion, AnimatePresence } from "framer-motion"
import { ArrowRight, Bot, Sparkles } from "lucide-react"
import { MarkdownRenderer } from "@/components/ui/markdown-renderer"
import { DraftCard } from "@/components/qa/draft-card"
import { cn } from "@/lib/utils"
import type { DraftPayload, MessageRole, QaMessage, SourceArticle } from "@/lib/types"

export function ChatThread({
  messages,
  streamingContent,
  streamingStatus,
  isStreaming,
  className,
  suggestions,
  onSuggestion,
}: {
  messages: QaMessage[]
  streamingContent: string
  streamingStatus?: string | null
  isStreaming: boolean
  className?: string
  suggestions?: string[]
  onSuggestion?: (text: string) => void
}) {
  const endRef = useRef<HTMLDivElement>(null)
  const showEmpty =
    messages.length === 0 && !isStreaming && !streamingContent

  useEffect(() => {
    if (showEmpty) return
    endRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [messages, streamingContent, streamingStatus, isStreaming, showEmpty])

  return (
    <div className={cn("overflow-y-auto px-4 py-6 md:px-8", className)}>
      <div className="mx-auto w-full max-w-3xl space-y-7">
        {showEmpty ? (
          <EmptyState suggestions={suggestions} onSuggestion={onSuggestion} />
        ) : (
          <AnimatePresence initial={false}>
            {messages.map((m) => (
              <MessageItem key={m.id} role={m.role} content={m.content} sources={m.sources} draft={m.draft} imageUrls={m.imageUrls} />
            ))}
            {(isStreaming || streamingContent) && (
              <MessageItem
                role="assistant"
                content={streamingContent}
                statusText={streamingStatus}
                streaming={isStreaming}
              />
            )}
          </AnimatePresence>
        )}
        <div ref={endRef} />
      </div>
    </div>
  )
}

function EmptyState({
  suggestions,
  onSuggestion,
}: {
  suggestions?: string[]
  onSuggestion?: (text: string) => void
}) {
  return (
    <div className="flex flex-col items-center justify-center py-7 text-center">
      <motion.div
        initial={{ scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: "spring", stiffness: 300, damping: 24 }}
        className="mb-4"
      >
        <Sparkles className="size-7 text-[#2f5d50]" strokeWidth={1.5} />
      </motion.div>

      <h3 className="font-display text-3xl font-medium tracking-[-0.04em] text-[#1d211f]">
        向知识库提问
      </h3>
      <p className="mt-2 text-sm text-[#7a7e79]">
        支持多轮追问，AI 会记住上下文与你
      </p>

      {suggestions && suggestions.length > 0 && (
        <div className="mt-6 grid w-full max-w-2xl gap-3 sm:grid-cols-3">
          {suggestions.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => onSuggestion?.(s)}
              className="group flex min-h-24 w-full flex-col items-start justify-between gap-3 rounded-xl bg-[#efefe9] p-4 text-left text-sm font-medium text-[#505650] transition-transform hover:-translate-y-1 hover:text-[#1d211f]"
            >
              <span>{s}</span>
              <ArrowRight className="size-4 shrink-0 self-end text-[#64736b] transition-all group-hover:translate-x-0.5 group-hover:text-[#2f5d50]" />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function MessageItem({
  role,
  content,
  streaming,
  sources,
  draft,
  imageUrls,
  statusText,
}: {
  role: MessageRole
  content: string
  streaming?: boolean
  sources?: SourceArticle[]
  draft?: DraftPayload
  imageUrls?: string[]
  statusText?: string | null
}) {
  if (role === "user") {
    const imgs = imageUrls && imageUrls.length > 0 ? imageUrls : undefined
    return (
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
        className="flex flex-col items-end gap-1.5"
      >
        {imgs && (
          <div className="flex max-w-[80%] flex-wrap justify-end gap-1.5">
            {imgs.map((url, idx) => (
              <img
                key={url + idx}
                src={url}
                alt="附件"
                className="max-h-52 rounded-lg object-cover ring-1 ring-[#d8d9d2]"
              />
            ))}
          </div>
        )}
        {content && (
          <div className="max-w-[80%] whitespace-pre-wrap border-r-2 border-[#2f5d50] bg-[#e7eee9] px-4 py-3 text-sm leading-relaxed text-[#29443b]">
            {content}
          </div>
        )}
      </motion.div>
    )
  }

  // AI：无气泡，渐变头像 + 内容铺开（更适合长 Markdown，视觉更轻盈高级）
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
      className="flex gap-3"
    >
      <Bot className="mt-0.5 size-5 shrink-0 text-[#2f5d50]" strokeWidth={1.6} />
      <div className="min-w-0 flex-1 pt-0.5 text-[#4e544f]">
        {content ? (
          <MarkdownRenderer content={content} className="prose-sm max-w-none" />
        ) : (
          <span className="text-sm text-slate-400">{statusText || "思考中…"}</span>
        )}
        {content && streaming && statusText && (
          <div className="mt-2 text-xs text-[#7a7e79]">{statusText}</div>
        )}
        {streaming && (
          <span className="ml-0.5 inline-block h-4 w-[2px] animate-pulse bg-[#2f5d50] align-middle" />
        )}
        {sources && sources.length > 0 && <Recommendations sources={sources} />}
        {draft && <DraftCard draft={draft} />}
      </div>
    </motion.div>
  )
}

/** 回答下方「为您推荐」：命中知识库的文章，点击跳转详情页。 */
function Recommendations({ sources }: { sources: SourceArticle[] }) {
  return (
    <div className="mt-4 rounded-xl bg-[#efefe9] p-4">
      <div className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-[#626762]">
        <Sparkles className="size-3.5 text-[#2f5d50]" />
        为您推荐
      </div>
      <ol className="space-y-1.5">
        {sources.map((s, i) => (
          <li key={s.postId}>
            <Link
              href={`/app/posts/${s.postId}`}
              className="group flex items-start gap-2 text-sm text-[#626762] transition-colors hover:text-[#2f5d50]"
            >
              <span className="mt-0.5 flex size-4 shrink-0 items-center justify-center border border-[#aeb5af] text-[10px] font-medium text-[#2f5d50]">
                {i + 1}
              </span>
              <span className="underline-offset-2 group-hover:underline">
                {s.title || "未命名文章"}
              </span>
            </Link>
          </li>
        ))}
      </ol>
    </div>
  )
}
