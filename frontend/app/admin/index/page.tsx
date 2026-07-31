"use client"

import { useCallback, useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { ConfirmDialog, type ConfirmState } from "@/components/admin/dialogs"
import { useAuth } from "@/components/auth/auth-context"
import { adminService } from "@/lib/api/admin"
import { ApiError } from "@/lib/api/client"
import type {
  AdminKnowPostItem,
  IndexStats,
  PageResult,
  RebuildStatus,
} from "@/lib/types/admin"
import { Loader2, RefreshCw, Search } from "lucide-react"
import { cn } from "@/lib/utils"

const VISIBLE_BADGE: Record<string, string> = {
  public: "bg-primary/10 text-primary",
  private: "bg-slate-100 text-slate-600",
  followers: "bg-accent text-accent-foreground",
  school: "bg-secondary text-secondary-foreground",
  unlisted: "bg-amber-100 text-amber-700",
}

const PAGE_SIZE = 20

export default function AdminIndexPage() {
  const { tokens } = useAuth()
  const [stats, setStats] = useState<IndexStats | null>(null)
  const [statsLoading, setStatsLoading] = useState(false)
  const [postsLoading, setPostsLoading] = useState(false)
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")
  const [keyword, setKeyword] = useState("")
  const [visible, setVisible] = useState("")
  const [query, setQuery] = useState({ keyword: "", visible: "" })
  const [page, setPage] = useState(1)
  const [posts, setPosts] = useState<PageResult<AdminKnowPostItem> | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [rebuild, setRebuild] = useState<RebuildStatus | null>(null)
  const [confirm, setConfirm] = useState<ConfirmState | null>(null)

  const loadStats = useCallback(async () => {
    if (!tokens?.accessToken) return
    setStatsLoading(true)
    try {
      setStats(await adminService.getIndexStats(tokens.accessToken))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "加载失败")
    } finally {
      setStatsLoading(false)
    }
  }, [tokens?.accessToken])

  const loadPosts = useCallback(async () => {
    if (!tokens?.accessToken) return
    setPostsLoading(true)
    try {
      setPosts(await adminService.listPosts(tokens.accessToken, {
        keyword: query.keyword,
        status: "published",
        visible: query.visible,
        page,
        size: PAGE_SIZE,
      }))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "文章明细加载失败")
    } finally {
      setPostsLoading(false)
    }
  }, [page, query.keyword, query.visible, tokens?.accessToken])

  useEffect(() => {
    loadStats()
  }, [loadStats])

  useEffect(() => {
    loadPosts()
  }, [loadPosts])

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

  const refreshAll = async () => {
    setError("")
    await Promise.all([loadStats(), loadPosts()])
  }

  const searchPosts = () => {
    setError("")
    setNotice("")
    const nextQuery = { keyword: keyword.trim(), visible }
    if (page === 1 && nextQuery.keyword === query.keyword && nextQuery.visible === query.visible) {
      loadPosts()
      return
    }
    setPage(1)
    setQuery(nextQuery)
  }

  const rebuildPost = async (post: AdminKnowPostItem) => {
    if (!tokens?.accessToken) return
    setBusyId(post.id)
    setError("")
    setNotice("")
    try {
      const chunks = await adminService.rebuildRagPost(tokens.accessToken, post.id)
      setNotice(`《${post.title || "无标题"}》重建完成，共生成 ${chunks} 个向量切片。`)
      await Promise.all([loadStats(), loadPosts()])
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "文章重建失败")
    } finally {
      setBusyId(null)
    }
  }

  const deletePostIndex = async (post: AdminKnowPostItem) => {
    if (!tokens?.accessToken) return
    setBusyId(post.id)
    setError("")
    setNotice("")
    try {
      await adminService.deleteRagPostIndex(tokens.accessToken, post.id)
      setNotice(`已删除《${post.title || "无标题"}》的向量切片。`)
      await Promise.all([loadStats(), loadPosts()])
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "删除向量切片失败")
    } finally {
      setBusyId(null)
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

  const totalPages = posts ? Math.max(1, Math.ceil(posts.total / posts.size)) : 1
  const refreshing = statsLoading || postsLoading

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">索引库管理</h1>
        <Button variant="outline" className="h-9" onClick={refreshAll} disabled={refreshing}>
          {refreshing ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />}
          刷新
        </Button>
      </div>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}
      {notice && <div className="rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{notice}</div>}

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

      {/* 文章级索引明细 */}
      <section className="space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-base font-semibold text-slate-900">文章明细</h2>
            <p className="mt-1 text-xs text-slate-400">按已发布文章重建或删除向量切片</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Input
              placeholder="搜索文章标题"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") searchPosts()
              }}
              className="h-9 w-52"
            />
            <select
              value={visible}
              onChange={(event) => setVisible(event.target.value)}
              className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            >
              <option value="">全部可见性</option>
              <option value="public">公开</option>
              <option value="private">私密</option>
              <option value="followers">关注者</option>
              <option value="school">同校</option>
              <option value="unlisted">未列出</option>
            </select>
            <Button
              className="h-9"
              onClick={searchPosts}
              disabled={postsLoading}
            >
              <Search className="size-4" />
              查询
            </Button>
          </div>
        </div>

        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
          <table className="w-full min-w-[880px] text-sm">
            <thead className="bg-slate-50 text-left text-xs text-slate-500">
              <tr>
                <th className="px-4 py-3 font-medium">ID</th>
                <th className="px-4 py-3 font-medium">标题</th>
                <th className="px-4 py-3 font-medium">作者</th>
                <th className="px-4 py-3 font-medium">可见性</th>
                <th className="px-4 py-3 font-medium">发布时间</th>
                <th className="px-4 py-3 font-medium">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {postsLoading && (
                <tr>
                  <td colSpan={6} className="px-4 py-10 text-center text-slate-400">
                    <Loader2 className="mx-auto mb-2 size-5 animate-spin" />
                    正在加载文章明细
                  </td>
                </tr>
              )}
              {!postsLoading && posts?.items.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-10 text-center text-slate-400">
                    没有符合条件的已发布文章
                  </td>
                </tr>
              )}
              {!postsLoading && posts?.items.map((post) => {
                const busy = busyId === post.id
                return (
                  <tr key={post.id} className="transition-colors hover:bg-slate-50/80">
                    <td className="px-4 py-3 font-mono text-xs text-slate-500">{post.id}</td>
                    <td
                      className="max-w-[300px] truncate px-4 py-3 font-medium text-slate-800"
                      title={post.title ?? ""}
                    >
                      {post.title || "(无标题)"}
                    </td>
                    <td className="px-4 py-3 text-slate-600">{post.creatorNickname ?? post.creatorId}</td>
                    <td className="px-4 py-3">
                      <Badge className={cn("border-0", VISIBLE_BADGE[post.visible] || "bg-slate-100 text-slate-600")}>
                        {post.visible}
                      </Badge>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-slate-500">
                      {post.publishTime ? new Date(post.publishTime).toLocaleString() : "-"}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <Button
                          className="h-8 px-3 text-xs"
                          disabled={busy || !!rebuild?.running}
                          onClick={() => rebuildPost(post)}
                        >
                          {busy ? <Loader2 className="size-3.5 animate-spin" /> : null}
                          重建
                        </Button>
                        <Button
                          variant="outline"
                          className="h-8 px-3 text-xs text-red-600 hover:bg-red-50 hover:text-red-700"
                          disabled={busy || !!rebuild?.running}
                          onClick={() => setConfirm({
                            title: "删除向量切片",
                            description: `确认删除《${post.title || "无标题"}》的全部向量切片？文章本身不会被删除。`,
                            danger: true,
                            confirmText: "删除切片",
                            onConfirm: () => deletePostIndex(post),
                          })}
                        >
                          删除切片
                        </Button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        {posts && (
          <div className="flex items-center justify-between text-sm text-slate-500">
            <span>共 {posts.total} 篇已发布文章</span>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                className="h-8"
                disabled={page <= 1 || postsLoading}
                onClick={() => setPage((current) => current - 1)}
              >
                上一页
              </Button>
              <span className="flex h-8 items-center px-2 tabular-nums">{page} / {totalPages}</span>
              <Button
                variant="outline"
                className="h-8"
                disabled={page >= totalPages || postsLoading}
                onClick={() => setPage((current) => current + 1)}
              >
                下一页
              </Button>
            </div>
          </div>
        )}
      </section>

      {/* 全量重建 */}
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="flex items-center justify-between">
          <div className="text-sm font-medium text-slate-500">全量重建</div>
          <Button
            className="h-9"
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
                    className="h-full bg-primary transition-all"
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
