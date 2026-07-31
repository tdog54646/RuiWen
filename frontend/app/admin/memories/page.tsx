"use client"

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { ConfirmDialog, type ConfirmState } from "@/components/admin/dialogs"
import { useAuth } from "@/components/auth/auth-context"
import { adminService } from "@/lib/api/admin"
import { ApiError } from "@/lib/api/client"
import type { AdminMemoryItem, PageResult } from "@/lib/types/admin"
import { cn } from "@/lib/utils"

const SOURCE_BADGE: Record<string, string> = {
  auto: "bg-primary/10 text-primary",
  manual: "bg-secondary text-secondary-foreground",
}

export default function AdminMemoriesPage() {
  const { tokens } = useAuth()
  const [keyword, setKeyword] = useState("")
  const [userIdInput, setUserIdInput] = useState("")
  const [source, setSource] = useState("")
  const [page, setPage] = useState(1)
  const [data, setData] = useState<PageResult<AdminMemoryItem> | null>(null)
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
      const result = await adminService.listMemories(tokens.accessToken, {
        keyword,
        userId: userIdInput ? Number(userIdInput) : undefined,
        source,
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

  const run = async (id: string, fn: () => Promise<void>) => {
    setBusyId(id)
    setError("")
    try {
      await fn()
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
      <div>
        <h1 className="text-xl font-semibold text-slate-900">用户记忆</h1>
        <p className="mt-1 text-sm text-slate-500">AI 为用户总结/用户手写的记忆条目。可启用/禁用或删除不当内容。</p>
      </div>

      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white p-4">
        <Input
          placeholder="按内容搜索"
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
        <select value={source} onChange={(e) => setSource(e.target.value)} className="h-9 rounded-md border border-slate-200 px-2 text-sm">
          <option value="">全部来源</option>
          <option value="auto">AI 自动(auto)</option>
          <option value="manual">用户手写(manual)</option>
        </select>
        <Button onClick={() => { setPage(1); load() }} className="h-9">查询</Button>
      </div>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">用户</th>
              <th className="px-4 py-3 font-medium">分类</th>
              <th className="px-4 py-3 font-medium">内容</th>
              <th className="px-4 py-3 font-medium">来源</th>
              <th className="px-4 py-3 font-medium">启用</th>
              <th className="px-4 py-3 font-medium">更新时间</th>
              <th className="px-4 py-3 font-medium">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading && <tr><td colSpan={7} className="px-4 py-8 text-center text-slate-400">加载中...</td></tr>}
            {!loading && data && data.items.length === 0 && (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-slate-400">暂无数据</td></tr>
            )}
            {!loading && data?.items.map((m) => (
              <tr key={m.id} className="hover:bg-slate-50">
                <td className="px-4 py-3 text-slate-600">{m.userNickname ?? m.userId}</td>
                <td className="px-4 py-3 text-slate-600">{m.category}</td>
                <td className="max-w-[320px] px-4 py-3 text-slate-700">
                  <div className="line-clamp-2" title={m.content}>{m.content}</div>
                </td>
                <td className="px-4 py-3">
                  <Badge className={cn("border-0", SOURCE_BADGE[m.source] || "bg-slate-100 text-slate-600")}>{m.source}</Badge>
                </td>
                <td className="px-4 py-3">
                  <button
                    type="button"
                    aria-label="切换启用"
                    onClick={() => run(m.id, () => adminService.updateMemoryEnabled(tokens!.accessToken, m.id, !m.enabled))}
                    className={cn("relative h-5 w-9 rounded-full transition-colors", m.enabled ? "bg-primary" : "bg-slate-300")}
                  >
                    <span className={cn("absolute top-0.5 left-0.5 h-4 w-4 rounded-full bg-white transition-transform", m.enabled && "translate-x-4")} />
                  </button>
                </td>
                <td className="px-4 py-3 text-slate-500">{new Date(m.updatedAt).toLocaleString()}</td>
                <td className="px-4 py-3">
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs text-red-600"
                    disabled={busyId === m.id}
                    onClick={() => setConfirm({
                      title: "删除记忆",
                      description: "确认删除该记忆条目？",
                      danger: true,
                      confirmText: "删除",
                      onConfirm: () => run(m.id, () => adminService.deleteMemory(tokens!.accessToken, m.id)),
                    })}
                  >
                    删除
                  </Button>
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
