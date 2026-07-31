"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { ConfirmDialog, type ConfirmState } from "@/components/admin/dialogs"
import { useAuth } from "@/components/auth/auth-context"
import { adminService } from "@/lib/api/admin"
import { ApiError } from "@/lib/api/client"
import type { AdminConversationItem, PageResult } from "@/lib/types/admin"
import { cn } from "@/lib/utils"

export default function AdminConversationsPage() {
  const { tokens } = useAuth()
  const [keyword, setKeyword] = useState("")
  const [userIdInput, setUserIdInput] = useState("")
  const [includeDeleted, setIncludeDeleted] = useState(false)
  const [page, setPage] = useState(1)
  const [data, setData] = useState<PageResult<AdminConversationItem> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [busyId, setBusyId] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<ConfirmState | null>(null)
  const size = 20

  const load = async () => {
    if (!tokens?.accessToken) return
    setLoading(true)
    setError("")
    try {
      const result = await adminService.listConversations(tokens.accessToken, {
        keyword,
        userId: userIdInput ? Number(userIdInput) : undefined,
        includeDeleted,
        page,
        size,
      })
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
  }, [tokens?.accessToken, page])

  const handleDelete = async (id: string) => {
    setBusyId(id)
    setError("")
    try {
      await adminService.deleteConversation(tokens!.accessToken, id)
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
      <h1 className="text-xl font-semibold text-slate-900">会话审计</h1>

      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white p-4">
        <Input
          placeholder="按标题搜索"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          className="h-9 w-48"
        />
        <Input
          placeholder="用户ID"
          value={userIdInput}
          onChange={(e) => setUserIdInput(e.target.value)}
          className="h-9 w-32"
        />
        <label className="flex items-center gap-1.5 text-sm text-slate-600">
          <input
            type="checkbox"
            checked={includeDeleted}
            onChange={(e) => setIncludeDeleted(e.target.checked)}
            className="size-4 accent-slate-700"
          />
          含已删除
        </label>
        <Button onClick={() => { setPage(1); load() }} className="h-9">查询</Button>
      </div>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">会话ID</th>
              <th className="px-4 py-3 font-medium">标题</th>
              <th className="px-4 py-3 font-medium">用户</th>
              <th className="px-4 py-3 font-medium">消息数</th>
              <th className="px-4 py-3 font-medium">最近活跃</th>
              <th className="px-4 py-3 font-medium">状态</th>
              <th className="px-4 py-3 font-medium">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading && <tr><td colSpan={7} className="px-4 py-8 text-center text-slate-400">加载中...</td></tr>}
            {!loading && data && data.items.length === 0 && (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-slate-400">暂无数据</td></tr>
            )}
            {!loading && data?.items.map((c) => (
              <tr key={c.id} className="hover:bg-slate-50">
                <td className="px-4 py-3 font-mono text-xs text-slate-400">{c.id.slice(-10)}</td>
                <td className="max-w-[260px] truncate px-4 py-3 font-medium text-slate-800" title={c.title}>{c.title || "(无标题)"}</td>
                <td className="px-4 py-3 text-slate-600">{c.userNickname ?? c.userId}</td>
                <td className="px-4 py-3 text-slate-600">{c.messageCount}</td>
                <td className="px-4 py-3 text-slate-500">{c.lastMessageAt ? new Date(c.lastMessageAt).toLocaleString() : "-"}</td>
                <td className="px-4 py-3">
                  <Badge className={cn("border-0", c.deleted ? "bg-red-100 text-red-700" : "bg-emerald-100 text-emerald-700")}>
                    {c.deleted ? "已删除" : "正常"}
                  </Badge>
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-2">
                    <Link href={`/admin/conversations/${c.id}`}>
                      <Button variant="outline" className="h-7 px-2 text-xs">查看消息</Button>
                    </Link>
                    <Button
                      variant="outline"
                      className="h-7 px-2 text-xs text-red-600"
                      disabled={busyId === c.id || c.deleted}
                      onClick={() => setConfirm({
                        title: "删除会话",
                        description: "确认删除该会话？此为软删除，用户将不再看到该会话。",
                        danger: true,
                        confirmText: "删除",
                        onConfirm: () => handleDelete(c.id),
                      })}
                    >
                      删除
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {data && (
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
