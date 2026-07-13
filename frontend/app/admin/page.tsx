"use client"

import { useEffect, useState } from "react"
import { useAuth } from "@/components/auth/auth-context"
import { adminService } from "@/lib/api/admin"
import type { DashboardStats } from "@/lib/types/admin"
import { ApiError } from "@/lib/api/client"

function StatCard({ label, value, hint }: { label: string; value: number; hint?: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5">
      <div className="text-xs font-medium text-slate-500">{label}</div>
      <div className="mt-2 text-3xl font-semibold text-slate-900">{value.toLocaleString()}</div>
      {hint && <div className="mt-1 text-xs text-slate-400">{hint}</div>}
    </div>
  )
}

export default function AdminDashboardPage() {
  const { tokens } = useAuth()
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [error, setError] = useState("")

  useEffect(() => {
    if (!tokens?.accessToken) return
    adminService
      .getDashboardStats(tokens.accessToken)
      .then(setStats)
      .catch((err) => setError(err instanceof ApiError ? err.message : "加载失败"))
  }, [tokens?.accessToken])

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-900">仪表盘</h1>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}

      {!stats && !error && <div className="text-sm text-slate-400">加载中...</div>}

      {stats && (
        <>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
            <StatCard label="总用户数" value={stats.totalUsers} />
            <StatCard label="今日新增" value={stats.newUsersToday} />
            <StatCard label="被封禁用户" value={stats.bannedUsers} />
            <StatCard label="知文总数" value={stats.totalPosts} />
            <StatCard label="已发布知文" value={stats.publishedPosts} />
            <StatCard label="今日登录次数" value={stats.loginsToday} />
          </div>

          <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
            <StatCard label="AI 会话数" value={stats.totalConversations} hint="未删除会话" />
            <StatCard label="AI 消息数" value={stats.totalMessages} />
            <StatCard label="用户记忆条数" value={stats.totalMemories} />
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-semibold text-slate-700">角色分布</h2>
            <div className="mt-4 flex flex-wrap gap-3">
              {Object.entries(stats.roleDistribution).map(([role, count]) => (
                <div key={role} className="rounded-lg bg-slate-50 px-4 py-2">
                  <span className="text-sm font-medium text-slate-600">{role}</span>
                  <span className="ml-2 text-lg font-semibold text-slate-900">{count}</span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
