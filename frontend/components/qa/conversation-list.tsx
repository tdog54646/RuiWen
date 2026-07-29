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
      <div className="flex items-center justify-between px-4 py-4">
        <span className="text-sm font-semibold text-[#31443b]">
          会话
        </span>
        <Button
          size="xs"
          variant="ghost"
          onClick={onCreate}
          className="gap-1 rounded-md text-[#69706b] hover:text-[#1d211f]"
        >
          <Plus className="size-3.5" />
          新建
        </Button>
      </div>

      <div className="flex-1 space-y-0.5 overflow-y-auto px-2 py-2">
        {loading ? (
          <EmptyState loading />
        ) : conversations.length === 0 ? (
          <div className="px-2 py-10 text-center text-xs text-[#858984]">
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
                  "group relative flex items-center gap-1 rounded-lg px-3 py-2.5 text-sm transition-colors",
                  active
                    ? "bg-[#deded8] text-[#252a27]"
                    : "cursor-pointer text-[#5f645f] hover:bg-[#e5e5df] hover:text-[#252a27]",
                )}
              >
                {editing ? (
                  <>
                    <input
                      autoFocus
                      value={draft}
                      onChange={(e) => setDraft(e.target.value)}
                      className="h-7 flex-1 rounded-md border border-[#aeb5af] bg-[#fbfbf8] px-1.5 text-xs text-[#343936] outline-none focus:border-[#2f5d50]"
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
                          active ? "text-[#2f5d50]" : "text-[#7b807b]",
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
