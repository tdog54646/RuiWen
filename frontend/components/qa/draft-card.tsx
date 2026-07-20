"use client"

import { FileText, Tag } from "lucide-react"
import { MarkdownRenderer } from "@/components/ui/markdown-renderer"
import type { DraftPayload } from "@/lib/types"

/**
 * AI 录入文章生成的草稿卡片：展示标题/标签/正文预览，提示用户回复「发布」确认或说明修改。
 * 忠实走法 B（纯对话确认），不放发布按钮。
 */
export function DraftCard({ draft }: { draft: DraftPayload }) {
  return (
    <div className="mt-3 overflow-hidden rounded-xl border border-white/60 bg-white/50 backdrop-blur-md">
      <div className="flex items-center gap-1.5 border-b border-white/50 bg-white/40 px-3 py-2 text-xs font-semibold text-slate-500">
        <FileText className="size-3.5 text-violet-500" />
        文章草稿 · 待确认
      </div>
      <div className="space-y-2 px-3 py-3">
        <div className="text-sm font-semibold text-slate-800">
          {draft.title || "未命名文章"}
        </div>
        {draft.tags && draft.tags.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {draft.tags.map((t, i) => (
              <span
                key={`${t}-${i}`}
                className="inline-flex items-center gap-1 rounded-full bg-violet-100 px-2 py-0.5 text-[11px] font-medium text-violet-600"
              >
                <Tag className="size-2.5" />
                {t}
              </span>
            ))}
          </div>
        )}
        {draft.preview && (
          <div className="max-h-48 overflow-y-auto rounded-lg bg-white/40 px-3 py-2 text-sm text-slate-600">
            <MarkdownRenderer content={draft.preview} className="prose-sm max-w-none" />
          </div>
        )}
        <div className="rounded-lg bg-cyan-50/70 px-3 py-2 text-xs text-slate-500">
          确认无误请回复「发布」即可发布；需要修改请直接说明（如改标题、调整内容）。
        </div>
      </div>
    </div>
  )
}
