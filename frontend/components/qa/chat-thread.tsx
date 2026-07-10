"use client"

import { useEffect, useRef } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { ArrowRight, Bot, Sparkles } from "lucide-react"
import { MarkdownRenderer } from "@/components/ui/markdown-renderer"
import { cn } from "@/lib/utils"
import type { MessageRole, QaMessage } from "@/lib/types"

export function ChatThread({
  messages,
  streamingContent,
  isStreaming,
  className,
  suggestions,
  onSuggestion,
}: {
  messages: QaMessage[]
  streamingContent: string
  isStreaming: boolean
  className?: string
  suggestions?: string[]
  onSuggestion?: (text: string) => void
}) {
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [messages, streamingContent, isStreaming])

  const showEmpty =
    messages.length === 0 && !isStreaming && !streamingContent

  return (
    <div className={cn("overflow-y-auto px-4 py-6 md:px-8", className)}>
      <div className="mx-auto w-full max-w-3xl space-y-7">
        {showEmpty ? (
          <EmptyState suggestions={suggestions} onSuggestion={onSuggestion} />
        ) : (
          <AnimatePresence initial={false}>
            {messages.map((m) => (
              <MessageItem key={m.id} role={m.role} content={m.content} />
            ))}
            {(isStreaming || streamingContent) && (
              <MessageItem
                role="assistant"
                content={streamingContent}
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
    <div className="flex flex-col items-center justify-center py-14 text-center">
      <motion.div
        initial={{ scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: "spring", stiffness: 300, damping: 24 }}
        className="relative mb-6"
      >
        <div className="absolute inset-0 rounded-full bg-gradient-to-br from-cyan-400/40 to-violet-500/40 blur-2xl" />
        <div className="relative flex size-16 items-center justify-center rounded-2xl bg-gradient-to-br from-cyan-400 to-violet-500 shadow-lg shadow-violet-500/30">
          <Sparkles className="size-7 text-white" />
        </div>
      </motion.div>

      <h3 className="text-gradient text-2xl font-bold tracking-tight">
        向知识库提问
      </h3>
      <p className="mt-2 text-sm text-slate-400">
        支持多轮追问，AI 会记住上下文与你
      </p>

      {suggestions && suggestions.length > 0 && (
        <div className="mt-8 grid w-full max-w-lg gap-2">
          {suggestions.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => onSuggestion?.(s)}
              className="group flex items-center justify-between gap-3 rounded-xl border border-white/60 bg-white/40 px-4 py-3 text-left text-sm text-slate-600 backdrop-blur-md transition-all hover:border-cyan-400/40 hover:bg-white/70 hover:shadow-md hover:text-slate-800"
            >
              <span>{s}</span>
              <ArrowRight className="size-4 shrink-0 text-slate-300 transition-all group-hover:translate-x-0.5 group-hover:text-cyan-500" />
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
}: {
  role: MessageRole
  content: string
  streaming?: boolean
}) {
  if (role === "user") {
    return (
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
        className="flex justify-end"
      >
        <div className="max-w-[80%] whitespace-pre-wrap rounded-2xl rounded-br-md bg-gradient-to-br from-cyan-500 to-violet-500 px-4 py-2.5 text-sm leading-relaxed text-white shadow-md shadow-violet-500/20">
          {content}
        </div>
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
      <div className="flex size-8 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-cyan-400/20 to-violet-500/20 text-violet-600 ring-1 ring-white/50">
        <Bot className="size-4" />
      </div>
      <div className="min-w-0 flex-1 pt-0.5 text-slate-700">
        {content ? (
          <MarkdownRenderer content={content} className="prose-sm max-w-none" />
        ) : (
          <span className="text-sm text-slate-400">思考中…</span>
        )}
        {streaming && (
          <span className="ml-0.5 inline-block h-4 w-[2px] animate-pulse rounded-full bg-violet-500 align-middle" />
        )}
      </div>
    </motion.div>
  )
}
