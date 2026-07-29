"use client"

import { useEffect, useState } from "react"
import { useAuth } from "@/components/auth/auth-context"
import { adminService } from "@/lib/api/admin"
import type { DashboardStats } from "@/lib/types/admin"
import { ApiError } from "@/lib/api/client"

function StatCard({ label, value, hint }: { label: string; value: number; hint?: string }) {
  return (
    <div className="admin-stat-card rounded-2xl p-5 md:p-6">
      <div className="text-sm font-semibold text-[#666b66]">{label}</div>
      <div className="font-display mt-3 text-4xl font-medium tracking-[-0.045em] tabular-nums">{value.toLocaleString()}</div>
      {hint && <div className="mt-2 text-sm text-[#777b76]">{hint}</div>}
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
    <div className="space-y-9">
      <header className="py-2">
        <h1 className="font-display text-4xl font-medium tracking-[-0.05em] text-[#1d211f]">仪表盘</h1>
        <p className="mt-3 text-[15px] text-[#70746f]">用户、内容与 AI 服务的当前运行概况。</p>
      </header>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}

      {!stats && !error && <div className="text-sm text-[#7a7e79]">加载中...</div>}

      {stats && (
        <>
          <div className="admin-stat-grid grid grid-cols-2 gap-4 md:grid-cols-3">
            <StatCard label="总用户数" value={stats.totalUsers} />
            <StatCard label="今日新增" value={stats.newUsersToday} />
            <StatCard label="被封禁用户" value={stats.bannedUsers} />
            <StatCard label="知文总数" value={stats.totalPosts} />
            <StatCard label="已发布知文" value={stats.publishedPosts} />
            <StatCard label="今日登录次数" value={stats.loginsToday} />
            <StatCard label="AI 会话数" value={stats.totalConversations} hint="未删除会话" />
            <StatCard label="AI 消息数" value={stats.totalMessages} />
            <StatCard label="用户记忆条数" value={stats.totalMemories} />
          </div>

          <section className="rounded-2xl bg-[#e3e9e4] p-6 shadow-[0_18px_48px_-42px_rgba(35,55,46,0.45)]">
            <h2 className="font-display text-2xl font-medium tracking-[-0.035em] text-[#1d211f]">角色分布</h2>
            <div className="mt-5 grid gap-3 sm:grid-cols-3">
              {Object.entries(stats.roleDistribution).map(([role, count]) => (
                <div key={role} className="flex items-baseline justify-between rounded-xl bg-[#f5f6f3] px-4 py-3">
                  <span className="text-sm font-semibold text-[#666b66]">{role}</span>
                  <span className="font-mono text-lg font-semibold tabular-nums text-[#252a27]">{count}</span>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  )
}
