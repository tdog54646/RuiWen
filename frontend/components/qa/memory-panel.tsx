"use client"

import { useEffect, useState } from "react"
import { Brain, Check, Loader2, Pencil, Plus, Sparkles, Trash2, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { EmptyState, Toggle } from "@/components/ui/studio"
import { qaChatService } from "@/lib/api/qa-chat"
import { cn } from "@/lib/utils"
import { toast } from "sonner"
import type { MemoryEntry } from "@/lib/types"

export function MemoryPanel({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (v: boolean) => void
}) {
  const [entries, setEntries] = useState<MemoryEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [regenerating, setRegenerating] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editDraft, setEditDraft] = useState("")
  const [newCategory, setNewCategory] = useState("")
  const [newContent, setNewContent] = useState("")

  const load = async () => {
    setLoading(true)
    try {
      setEntries(await qaChatService.listMemories())
    } catch {
      toast.error("加载记忆失败")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) load()
  }, [open])

  const regenerate = async () => {
    setRegenerating(true)
    try {
      setEntries(await qaChatService.regenerateMemories())
      toast.success("已基于近期对话重新生成")
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "重新生成失败")
    } finally {
      setRegenerating(false)
    }
  }

  const add = async () => {
    const content = newContent.trim()
    if (!content) return
    try {
      const created = await qaChatService.createMemory({
        category: newCategory.trim() || "其他",
        content,
      })
      setEntries((prev) => [...prev, created])
      setNewCategory("")
      setNewContent("")
      toast.success("已添加")
    } catch {
      toast.error("添加失败")
    }
  }

  const saveEdit = async (entry: MemoryEntry) => {
    try {
      const updated = await qaChatService.updateMemory(entry.id, {
        content: editDraft.trim() || entry.content,
      })
      setEntries((prev) => prev.map((m) => (m.id === entry.id ? updated : m)))
      setEditingId(null)
    } catch {
      toast.error("保存失败")
    }
  }

  const toggleEnabled = async (entry: MemoryEntry) => {
    setEntries((prev) =>
      prev.map((m) => (m.id === entry.id ? { ...m, enabled: !m.enabled } : m)),
    )
    try {
      await qaChatService.updateMemory(entry.id, { enabled: !entry.enabled })
    } catch {
      setEntries((prev) =>
        prev.map((m) => (m.id === entry.id ? { ...m, enabled: entry.enabled } : m)),
      )
      toast.error("更新失败")
    }
  }

  const remove = async (entry: MemoryEntry) => {
    try {
      await qaChatService.deleteMemory(entry.id)
      setEntries((prev) => prev.filter((m) => m.id !== entry.id))
    } catch {
      toast.error("删除失败")
    }
  }

  const groups = entries.reduce<Record<string, MemoryEntry[]>>((acc, m) => {
    const key = m.category || "其他"
    ;(acc[key] ??= []).push(m)
    return acc
  }, {})

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full sm:max-w-md">
        <SheetHeader>
          <div className="flex items-center justify-between pr-8">
            <SheetTitle className="flex items-center gap-2">
              <Brain className="size-4 text-[#2f5d50]" />
              用户记忆
            </SheetTitle>
            <Button
              size="sm"
              variant="outline"
              onClick={regenerate}
              disabled={regenerating}
              className="gap-1"
            >
              {regenerating ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <Sparkles className="size-3.5" />
              )}
              重新生成
            </Button>
          </div>
          <SheetDescription>
            AI 会基于这些记忆个性化回答。自动条目由 AI 总结，手动条目由你维护。
          </SheetDescription>
        </SheetHeader>

        <div className="flex-1 space-y-4 overflow-y-auto px-4">
          {loading ? (
            <EmptyState loading />
          ) : entries.length === 0 ? (
            <EmptyState>暂无记忆，多对话几轮或点击“重新生成”</EmptyState>
          ) : (
            Object.entries(groups).map(([cat, items]) => (
              <div key={cat} className="space-y-2">
                <div className="text-sm font-semibold text-[#555a56]">
                  {cat}
                </div>
                {items.map((m) => (
                  <div
                    key={m.id}
                    className={cn(
                      "rounded-xl bg-[#efefe9] p-3",
                      !m.enabled && "opacity-50",
                    )}
                  >
                    {editingId === m.id ? (
                      <div className="space-y-2">
                        <textarea
                          autoFocus
                          value={editDraft}
                          onChange={(e) => setEditDraft(e.target.value)}
                          rows={2}
                          className="w-full resize-none rounded-lg border border-[#cfd1ca] bg-[#fbfbf8] p-2 text-sm outline-none focus:border-[#2f5d50]"
                        />
                        <div className="flex justify-end gap-1">
                          <Button
                            size="xs"
                            variant="ghost"
                            onClick={() => setEditingId(null)}
                            className="gap-1"
                          >
                            <X className="size-3" />
                            取消
                          </Button>
                          <Button
                            size="xs"
                            onClick={() => saveEdit(m)}
                            className="gap-1"
                          >
                            <Check className="size-3" />
                            保存
                          </Button>
                        </div>
                      </div>
                    ) : (
                      <div className="flex items-start gap-2">
                        <div className="flex-1 text-sm text-slate-700">{m.content}</div>
                        <div className="flex shrink-0 items-center gap-1">
                          <span className="text-sm text-[#777b76]">
                            {m.source === "auto" ? "自动" : "手动"}
                          </span>
                          <Toggle checked={m.enabled} onChange={() => toggleEnabled(m)} />
                          <Button
                            size="icon-xs"
                            variant="ghost"
                            title="编辑"
                            onClick={() => {
                              setEditingId(m.id)
                              setEditDraft(m.content)
                            }}
                          >
                            <Pencil className="size-3" />
                          </Button>
                          <Button
                            size="icon-xs"
                            variant="ghost"
                            title="删除"
                            onClick={() => remove(m)}
                          >
                            <Trash2 className="size-3" />
                          </Button>
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            ))
          )}
        </div>

        <div className="bg-[#efefe9] p-3">
          <div className="flex gap-2">
            <input
              value={newCategory}
              onChange={(e) => setNewCategory(e.target.value)}
              placeholder="分类"
              className="h-9 w-24 rounded-lg border border-[#cfd1ca] bg-[#fbfbf8] px-2 text-sm outline-none focus:border-[#2f5d50]"
            />
            <input
              value={newContent}
              onChange={(e) => setNewContent(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") add()
              }}
              placeholder="新增一条记忆…"
              className="h-9 flex-1 rounded-lg border border-[#cfd1ca] bg-[#fbfbf8] px-2 text-sm outline-none focus:border-[#2f5d50]"
            />
            <Button size="icon" onClick={add} disabled={!newContent.trim()}>
              <Plus className="size-4" />
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  )
}
