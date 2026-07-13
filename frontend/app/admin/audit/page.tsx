"use client"

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { useAuth } from "@/components/auth/auth-context"
import { adminService } from "@/lib/api/admin"
import { ApiError } from "@/lib/api/client"
import type { LoginLog, PageResult } from "@/lib/types/admin"
import { cn } from "@/lib/utils"

const STATUS_BADGE: Record<string, string> = {
  SUCCESS: "bg-emerald-100 text-emerald-700",
  FAILED: "bg-red-100 text-red-700",
}

export default function AdminAuditPage() {
  const { tokens } = useAuth()
  const [identifier, setIdentifier] = useState("")
  const [status, setStatus] = useState("")
  const [channel, setChannel] = useState("")
  const [page, setPage] = useState(1)
  const [data, setData] = useState<PageResult<LoginLog> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const size = 20

  const load = async () => {
    if (!tokens?.accessToken) return
    setLoading(true)
    setError("")
    try {
      const result = await adminService.listLoginLogs(tokens.accessToken, { identifier, status, channel, page, size })
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

  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-900">登录审计</h1>

      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white p-4">
        <Input
          placeholder="标识（手机号/邮箱）"
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
          className="h-9 w-48"
        />
        <select value={status} onChange={(e) => setStatus(e.target.value)} className="h-9 rounded-md border border-slate-200 px-2 text-sm">
          <option value="">全部状态</option>
          <option value="SUCCESS">成功</option>
          <option value="FAILED">失败</option>
        </select>
        <select value={channel} onChange={(e) => setChannel(e.target.value)} className="h-9 rounded-md border border-slate-200 px-2 text-sm">
          <option value="">全部渠道</option>
          <option value="PASSWORD">密码</option>
          <option value="CODE">验证码</option>
          <option value="REGISTER">注册</option>
        </select>
        <Button onClick={() => { setPage(1); load() }} className="h-9 bg-slate-900 text-white hover:bg-slate-800">查询</Button>
      </div>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">时间</th>
              <th className="px-4 py-3 font-medium">用户ID</th>
              <th className="px-4 py-3 font-medium">标识</th>
              <th className="px-4 py-3 font-medium">渠道</th>
              <th className="px-4 py-3 font-medium">状态</th>
              <th className="px-4 py-3 font-medium">IP</th>
              <th className="px-4 py-3 font-medium">User-Agent</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading && <tr><td colSpan={7} className="px-4 py-8 text-center text-slate-400">加载中...</td></tr>}
            {!loading && data && data.items.length === 0 && (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-slate-400">暂无数据</td></tr>
            )}
            {!loading && data?.items.map((log) => (
              <tr key={log.id} className="hover:bg-slate-50">
                <td className="px-4 py-3 text-slate-500">{new Date(log.createdAt).toLocaleString()}</td>
                <td className="px-4 py-3 text-slate-500">{log.userId ?? "-"}</td>
                <td className="px-4 py-3 text-slate-600">{log.identifier}</td>
                <td className="px-4 py-3 text-slate-600">{log.channel}</td>
                <td className="px-4 py-3">
                  <Badge className={cn("border-0", STATUS_BADGE[log.status] || "bg-slate-100 text-slate-600")}>{log.status}</Badge>
                </td>
                <td className="px-4 py-3 text-slate-500">{log.ip || "-"}</td>
                <td className="max-w-[240px] truncate px-4 py-3 text-slate-400" title={log.userAgent ?? ""}>{log.userAgent || "-"}</td>
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
    </div>
  )
}
