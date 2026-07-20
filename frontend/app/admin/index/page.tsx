"use client"

import { useCallback, useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { ConfirmDialog, type ConfirmState } from "@/components/admin/dialogs"
import { useAuth } from "@/components/auth/auth-context"
import { adminService } from "@/lib/api/admin"
import { ApiError } from "@/lib/api/client"
import type { IndexStats, RebuildStatus } from "@/lib/types/admin"
import { RefreshCw, Loader2 } from "lucide-react"
import { cn } from "@/lib/utils"

const VISIBLE_BADGE: Record<string, string> = {
  public: "bg-emerald-100 text-emerald-700",
  private: "bg-slate-100 text-slate-600",
  followers: "bg-blue-100 text-blue-700",
  school: "bg-violet-100 text-violet-700",
  unlisted: "bg-amber-100 text-amber-700",
}

export default function AdminIndexPage() {
  const { tokens } = useAuth()
  const [stats, setStats] = useState<IndexStats | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [postId, setPostId] = useState("")
  const [busy, setBusy] = useState(false)
  const [rebuild, setRebuild] = useState<RebuildStatus | null>(null)
  const [confirm, setConfirm] = useState<ConfirmState | null>(null)

  const loadStats = useCallback(async () => {
    if (!tokens?.accessToken) return
    setLoading(true)
    setError("")
    try {
      setStats(await adminService.getIndexStats(tokens.accessToken))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "加载失败")
    } finally {
      setLoading(false)
    }
  }, [tokens?.accessToken])

  useEffect(() => {
    loadStats()
  }, [loadStats])

  // 轮询全量重建进度
  const pollStatus = useCallback(async () => {
    if (!tokens?.accessToken) return
    try {
      setRebuild(await adminService.getRebuildAllStatus(tokens.accessToken))
    } catch {
      // 忽略轮询错误
    }
  }, [tokens?.accessToken])

  useEffect(() => {
    pollStatus()
    if (!rebuild?.running) return
    const t = setInterval(pollStatus, 2000)
    return () => clearInterval(t)
  }, [pollStatus, rebuild?.running])

  const runOne = async (fn: () => Promise<unknown>) => {
    if (!postId.trim()) return
    setBusy(true)
    setError("")
    try {
      await fn()
      await loadStats()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "操作失败")
    } finally {
      setBusy(false)
    }
  }

  const startRebuildAll = async () => {
    if (!tokens?.accessToken) return
    setError("")
    try {
      setRebuild(await adminService.rebuildAllRagIndex(tokens.accessToken))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "触发失败")
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">索引库管理</h1>
        <Button variant="outline" className="h-9" onClick={loadStats} disabled={loading}>
          {loading ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />}
          刷新
        </Button>
      </div>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}

      {/* 统计 */}
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="text-sm font-medium text-slate-500">索引统计</div>
        <div className="mt-3 flex flex-wrap gap-8">
          <div>
            <div className="text-2xl font-semibold text-slate-900">{stats?.totalChunks ?? "-"}</div>
            <div className="text-xs text-slate-400">向量切片数</div>
          </div>
          <div>
            <div className="text-2xl font-semibold text-slate-900">{stats?.distinctPosts ?? "-"}</div>
            <div className="text-xs text-slate-400">已索引知文数</div>
          </div>
        </div>
        <div className="mt-4">
          <div className="text-xs text-slate-400">按可见性分布</div>
          <div className="mt-2 flex flex-wrap gap-2">
            {stats?.byVisible?.length ? (
              stats.byVisible.map((b) => (
                <Badge key={b.visible} className={cn("border-0", VISIBLE_BADGE[b.visible] || "bg-slate-100 text-slate-600")}>
                  {b.visible}: {b.count}
                </Badge>
              ))
            ) : (
              <span className="text-sm text-slate-400">暂无数据</span>
            )}
          </div>
        </div>
      </div>

      {/* 单篇操作 */}
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="text-sm font-medium text-slate-500">单篇操作</div>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Input
            placeholder="输入知文 ID"
            value={postId}
            onChange={(e) => setPostId(e.target.value)}
            className="h-9 w-56"
          />
          <Button
            className="h-9 bg-slate-900 text-white hover:bg-slate-800"
            disabled={busy || !postId.trim()}
            onClick={() => runOne(() => adminService.rebuildRagPost(tokens!.accessToken, postId.trim()))}
          >
            {busy ? <Loader2 className="size-4 animate-spin" /> : null}
            重建
          </Button>
          <Button
            variant="outline"
            className="h-9 text-red-600"
            disabled={busy || !postId.trim()}
            onClick={() => setConfirm({
              title: "删除向量切片",
              description: `确认删除知文 ${postId.trim()} 的向量切片？`,
              danger: true,
              confirmText: "删除",
              onConfirm: () => runOne(() => adminService.deleteRagPostIndex(tokens!.accessToken, postId.trim())),
            })}
          >
            删除切片
          </Button>
        </div>
      </div>

      {/* 全量重建 */}
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="flex items-center justify-between">
          <div className="text-sm font-medium text-slate-500">全量重建</div>
          <Button
            className="h-9 bg-slate-900 text-white hover:bg-slate-800"
            disabled={!!rebuild?.running}
            onClick={startRebuildAll}
          >
            {rebuild?.running ? "重建中..." : "开始全量重建"}
          </Button>
        </div>
        <div className="mt-3 text-sm text-slate-600">
          {rebuild ? (
            <div className="space-y-2">
              <div>状态：{rebuild.message}　进度：{rebuild.done} / {rebuild.total}（失败 {rebuild.failed}）</div>
              {rebuild.total > 0 && (
                <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100">
                  <div
                    className="h-full bg-slate-900 transition-all"
                    style={{ width: `${Math.round((rebuild.done / rebuild.total) * 100)}%` }}
                  />
                </div>
              )}
            </div>
          ) : (
            <span className="text-slate-400">未运行</span>
          )}
        </div>
        <p className="mt-3 text-xs text-slate-400">
          全量重建会遍历所有已发布知文重新生成向量切片（含非公开，进入作者私有库）。每篇需拉取正文 + embedding，数据量大时耗时较长，可关闭页面、稍后回来查看进度。
        </p>
      </div>

      {/* 删除确认弹窗 */}
      <ConfirmDialog state={confirm} onClose={() => setConfirm(null)} />
    </div>
  )
}
