"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { useParams } from "next/navigation"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { ConfirmDialog, type ConfirmState } from "@/components/admin/dialogs"
import { ArrowLeft } from "lucide-react"
import { useAuth } from "@/components/auth/auth-context"
import { adminService } from "@/lib/api/admin"
import { ApiError } from "@/lib/api/client"
import type { AdminMessageItem, PageResult } from "@/lib/types/admin"
import { cn } from "@/lib/utils"

export default function AdminConversationDetailPage() {
  const params = useParams<{ id: string }>()
  const conversationId = params.id
  const { tokens } = useAuth()
  const [data, setData] = useState<PageResult<AdminMessageItem> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [busyId, setBusyId] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<ConfirmState | null>(null)
  const [page, setPage] = useState(1)
  const size = 50

  const load = async () => {
    if (!tokens?.accessToken || !conversationId) return
    setLoading(true)
    setError("")
    try {
      const result = await adminService.listMessages(tokens.accessToken, conversationId, page, size)
      setData(result)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "加载失败")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tokens?.accessToken, conversationId, page])

  const handleDeleteMessage = async (id: string) => {
    setBusyId(id)
    setError("")
    try {
      await adminService.deleteMessage(tokens!.accessToken, id)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "操作失败")
    } finally {
      setBusyId(null)
    }
  }

  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Link href="/admin/conversations" className="flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
          <ArrowLeft className="size-4" /> 返回列表
        </Link>
      </div>
      <div>
        <h1 className="text-xl font-semibold text-slate-900">会话消息</h1>
        <p className="mt-1 font-mono text-xs text-slate-400">会话 ID：{conversationId}</p>
      </div>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}

      <div className="space-y-3">
        {loading && <div className="text-sm text-slate-400">加载中...</div>}
        {!loading && data && data.items.length === 0 && (
          <div className="rounded-lg bg-slate-50 px-4 py-8 text-center text-sm text-slate-400">该会话暂无消息</div>
        )}
        {!loading && data?.items.map((m) => {
          const isUser = m.role === "user"
          return (
            <div key={m.id} className={cn("flex", isUser ? "justify-start" : "justify-end")}>
              <div className={cn("max-w-[75%] rounded-xl border p-3", isUser ? "border-slate-200 bg-white" : "border-violet-200 bg-violet-50")}>
                <div className="mb-1 flex items-center gap-2">
                  <Badge className={cn("border-0", isUser ? "bg-slate-100 text-slate-600" : "bg-violet-100 text-violet-700")}>
                    {isUser ? "用户" : "AI"}
                  </Badge>
                  <span className="text-xs text-slate-400">{new Date(m.createdAt).toLocaleString()}</span>
                  {m.status !== "completed" && (
                    <Badge className="border-0 bg-amber-100 text-amber-700">{m.status}</Badge>
                  )}
                </div>
                <div className="whitespace-pre-wrap break-words text-sm text-slate-700">{m.content}</div>
                <div className="mt-2 text-right">
                  <Button
                    variant="outline"
                    className="h-6 px-2 text-xs text-red-600"
                    disabled={busyId === m.id}
                    onClick={() => setConfirm({
                      title: "删除消息",
                      description: "确认删除该消息？此操作不可恢复。",
                      danger: true,
                      confirmText: "删除",
                      onConfirm: () => handleDeleteMessage(m.id),
                    })}
                  >
                    删除
                  </Button>
                </div>
              </div>
            </div>
          )
        })}
      </div>

      {data && data.total > size && (
        <div className="flex items-center justify-between text-sm text-slate-500">
          <span>共 {data.total} 条</span>
          <div className="flex gap-2">
            <Button variant="outline" className="h-8" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>上一页</Button>
            <span className="flex h-8 items-center px-2">{page} / {totalPages}</span>
            <Button variant="outline" className="h-8" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>下一页</Button>
          </div>
        </div>
      )}

      {/* 删除确认弹窗 */}
      <ConfirmDialog state={confirm} onClose={() => setConfirm(null)} />
    </div>
  )
}
