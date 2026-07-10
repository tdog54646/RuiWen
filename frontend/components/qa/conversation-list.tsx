"use client"

import { useState, type KeyboardEvent } from "react"
import { Check, MessageSquare, Pencil, Plus, Trash2, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/studio"
import { cn } from "@/lib/utils"
import type { Conversation } from "@/lib/types"

export function ConversationList({
  conversations,
  activeId,
  loading,
  onSelect,
  onCreate,
  onRename,
  onDelete,
}: {
  conversations: Conversation[]
  activeId: string | null
  loading: boolean
  onSelect: (id: string) => void
  onCreate: () => void
  onRename: (id: string, title: string) => void
  onDelete: (id: string) => void
}) {
  const [editingId, setEditingId] = useState<string | null>(null)
  const [draft, setDraft] = useState("")

  const commitRename = (id: string, fallback: string) => {
    onRename(id, draft.trim() || fallback)
    setEditingId(null)
  }

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between border-b border-white/40 px-4 py-3.5">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          会话
        </span>
        <Button
          size="xs"
          variant="ghost"
          onClick={onCreate}
          className="gap-1 text-slate-500 hover:text-slate-800"
        >
          <Plus className="size-3.5" />
          新建
        </Button>
      </div>

      <div className="flex-1 space-y-0.5 overflow-y-auto px-2 py-2">
        {loading ? (
          <EmptyState loading />
        ) : conversations.length === 0 ? (
          <div className="px-2 py-10 text-center text-xs text-slate-400">
            点击「新建」开始第一个会话
          </div>
        ) : (
          conversations.map((c) => {
            const active = c.id === activeId
            const editing = editingId === c.id
            return (
              <div
                key={c.id}
                className={cn(
                  "group relative flex items-center gap-1 rounded-xl px-2.5 py-2 text-sm transition-all",
                  active
                    ? "bg-gradient-to-r from-cyan-500/10 to-violet-500/10 text-slate-800 ring-1 ring-white/60 backdrop-blur-md"
                    : "cursor-pointer text-slate-500 hover:bg-white/50 hover:text-slate-700",
                )}
              >
                {active && (
                  <span className="absolute left-0 top-1/2 h-5 w-1 -translate-y-1/2 rounded-full bg-gradient-to-b from-cyan-400 to-violet-500" />
                )}

                {editing ? (
                  <>
                    <input
                      autoFocus
                      value={draft}
                      onChange={(e) => setDraft(e.target.value)}
                      className="h-6 flex-1 rounded bg-white/80 px-1.5 text-xs text-slate-700 outline-none ring-1 ring-cyan-400/50"
                      onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                        if (e.key === "Enter") commitRename(c.id, c.title)
                        if (e.key === "Escape") setEditingId(null)
                      }}
                    />
                    <Button
                      size="icon-xs"
                      variant="ghost"
                      onClick={() => commitRename(c.id, c.title)}
                    >
                      <Check className="size-3" />
                    </Button>
                    <Button
                      size="icon-xs"
                      variant="ghost"
                      onClick={() => setEditingId(null)}
                    >
                      <X className="size-3" />
                    </Button>
                  </>
                ) : (
                  <>
                    <button
                      className="flex flex-1 items-center gap-2 truncate text-left"
                      onClick={() => onSelect(c.id)}
                    >
                      <MessageSquare
                        className={cn(
                          "size-3.5 shrink-0",
                          active ? "text-violet-500" : "text-slate-400",
                        )}
                      />
                      <span className="truncate">{c.title}</span>
                    </button>
                    <div className="hidden shrink-0 gap-0.5 group-hover:flex">
                      <Button
                        size="icon-xs"
                        variant="ghost"
                        title="重命名"
                        onClick={() => {
                          setEditingId(c.id)
                          setDraft(c.title)
                        }}
                      >
                        <Pencil className="size-3" />
                      </Button>
                      <Button
                        size="icon-xs"
                        variant="ghost"
                        title="删除"
                        onClick={() => onDelete(c.id)}
                      >
                        <Trash2 className="size-3" />
                      </Button>
                    </div>
                  </>
                )}
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
