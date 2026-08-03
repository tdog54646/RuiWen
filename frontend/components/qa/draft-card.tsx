"use client"

import { useState } from "react"
import { Check, FileText, Loader2, Send, Tag } from "lucide-react"
import { MarkdownRenderer } from "@/components/ui/markdown-renderer"
import { knowpostService } from "@/lib/api/knowpost"
import type { DraftPayload } from "@/lib/types"

/**
 * AI 只能生成草稿；发布必须由用户在本卡片中明确点击并再次确认。
 * 发布接口绑定卡片携带的精确 postId，不存在“最近草稿”回退。
 */
export function DraftCard({ draft }: { draft: DraftPayload }) {
  const [status, setStatus] = useState<"idle" | "publishing" | "published">(
    "idle",
  )
  const [error, setError] = useState<string | null>(null)

  const publish = async () => {
    if (status !== "idle") return
    const confirmed = window.confirm(
      `确认发布《${draft.title || "未命名文章"}》吗？发布后将按当前可见性展示。`,
    )
    if (!confirmed) return

    setStatus("publishing")
    setError(null)
    try {
      await knowpostService.publish(draft.postId)
      setStatus("published")
    } catch (e) {
      setStatus("idle")
      setError(e instanceof Error ? e.message : "发布失败，请稍后重试")
    }
  }

  return (
    <div className="mt-4 overflow-hidden rounded-xl bg-[#efefe9] p-4">
      <div className="flex items-center gap-1.5 text-sm font-semibold text-[#626762]">
        <FileText className="size-3.5 text-[#2f5d50]" />
        文章草稿 · 待确认
      </div>
      <div className="mt-3 space-y-3">
        <div className="text-sm font-semibold text-slate-800">
          {draft.title || "未命名文章"}
        </div>
        {draft.tags && draft.tags.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {draft.tags.map((t, i) => (
              <span
                key={`${t}-${i}`}
                className="inline-flex items-center gap-1 rounded-md bg-[#e2e2dc] px-2 py-1 text-sm font-medium text-[#536a60]"
              >
                <Tag className="size-2.5" />
                {t}
              </span>
            ))}
          </div>
        )}
        {draft.preview && (
          <div className="max-h-48 overflow-y-auto rounded-lg bg-[#fbfbf8] px-3 py-2 text-sm text-[#626762]">
            <MarkdownRenderer content={draft.preview} className="prose-sm max-w-none" />
          </div>
        )}
        <div className="rounded-lg bg-[#e7eee9] px-3 py-2 text-sm text-[#59615c]">
          发布不会由 AI 自动执行。请核对内容后点击下方按钮；需要修改可继续在对话中说明。
        </div>
        <button
          type="button"
          onClick={() => void publish()}
          disabled={status !== "idle"}
          className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-lg bg-[#1d211f] px-4 text-sm font-semibold text-white transition-colors hover:bg-[#2f5d50] disabled:cursor-not-allowed disabled:bg-[#a4a7a2]"
        >
          {status === "publishing" ? (
            <>
              <Loader2 className="size-4 animate-spin" />
              正在发布
            </>
          ) : status === "published" ? (
            <>
              <Check className="size-4" />
              已发布
            </>
          ) : (
            <>
              <Send className="size-4" />
              确认发布
            </>
          )}
        </button>
        {error && (
          <p role="alert" className="text-sm text-[#a33a32]">
            {error}
          </p>
        )}
      </div>
    </div>
  )
}
