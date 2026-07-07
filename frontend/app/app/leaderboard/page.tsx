"use client"

import Link from "next/link"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useAuth } from "@/components/auth/auth-context"
import { Button } from "@/components/ui/button"
import { UserAvatar } from "@/components/ui/user-avatar"
import {
  EmptyState,
  GlassCard,
  MessageBanner,
  PageHeader,
  SectionLabel,
  StatusChip,
  StudioShell,
} from "@/components/ui/studio"
import { leaderboardService } from "@/lib/api/leaderboard"
import type {
  LeaderboardTopItem,
  LeaderboardUserPosition,
  RankType,
} from "@/lib/types/leaderboard"
import { Trophy } from "lucide-react"
import { cn } from "@/lib/utils"

function formatDateToYYYYMMDD(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}${month}${day}`
}

function formatScore(score: number): string {
  return new Intl.NumberFormat("zh-CN").format(score)
}

function rankTypeLabel(rankType: RankType): string {
  if (rankType === "EXACT") return "精确名次"
  if (rankType === "ESTIMATE") return "预估名次"
  return "未上榜"
}

function rankBadgeClass(rank: number): string {
  if (rank === 1) return "bg-gradient-to-br from-amber-300 to-orange-500 text-white"
  if (rank === 2) return "bg-gradient-to-br from-slate-300 to-slate-500 text-white"
  if (rank === 3) return "bg-gradient-to-br from-orange-300 to-amber-700 text-white"
  return "bg-white/60 text-slate-500"
}

export default function LeaderboardPage() {
  const { user } = useAuth()
  const date = useMemo(() => formatDateToYYYYMMDD(new Date()), [])
  const leaderboardType = "like"

  const [items, setItems] = useState<LeaderboardTopItem[]>([])
  const [loadingTop, setLoadingTop] = useState(true)
  const [topError, setTopError] = useState<string | null>(null)

  const [myPosition, setMyPosition] = useState<LeaderboardUserPosition | null>(null)
  const [loadingMine, setLoadingMine] = useState(false)
  const [mineError, setMineError] = useState<string | null>(null)
  const topReqSeqRef = useRef(0)
  const mineReqSeqRef = useRef(0)

  const loadTop = useCallback(async () => {
    const requestSeq = ++topReqSeqRef.current
    setLoadingTop(true)
    setTopError(null)
    try {
      const resp = await leaderboardService.top({
        leaderboardType,
        date,
        offset: 0,
        limit: 20,
      })
      if (requestSeq !== topReqSeqRef.current) return
      setItems(resp.items ?? [])
    } catch (err) {
      if (requestSeq !== topReqSeqRef.current) return
      setTopError(err instanceof Error ? err.message : "加载排行榜失败")
      setItems([])
    } finally {
      if (requestSeq === topReqSeqRef.current) {
        setLoadingTop(false)
      }
    }
  }, [date, leaderboardType])

  useEffect(() => {
    void loadTop()
  }, [loadTop])

  const loadMine = useCallback(async () => {
    const requestSeq = ++mineReqSeqRef.current
    if (!user?.id) {
      setMyPosition(null)
      setMineError(null)
      setLoadingMine(false)
      return
    }

    setLoadingMine(true)
    setMineError(null)
    try {
      const resp = await leaderboardService.userPosition({
        leaderboardType,
        date,
      })
      if (requestSeq !== mineReqSeqRef.current) return
      setMyPosition(resp)
    } catch (err) {
      if (requestSeq !== mineReqSeqRef.current) return
      setMyPosition(null)
      setMineError(err instanceof Error ? err.message : "加载个人排行失败")
    } finally {
      if (requestSeq === mineReqSeqRef.current) {
        setLoadingMine(false)
      }
    }
  }, [date, leaderboardType, user?.id])

  useEffect(() => {
    void loadMine()
  }, [loadMine])

  return (
    <StudioShell>
      <PageHeader
        badge={
          <StatusChip icon={Trophy} tone="amber">
            今日点赞榜
          </StatusChip>
        }
        title="排行榜"
        subtitle={`Top 20 · ${date}`}
        chips={<StatusChip>更新中</StatusChip>}
      />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        {/* Top 20 榜单 */}
        <GlassCard
          delay={0.05}
          disableHover
          className="lg:col-span-8"
          contentClassName="flex flex-col gap-3"
        >
          <SectionLabel>今日 Top 20</SectionLabel>

          <div className="grid grid-cols-[56px_1fr_110px] items-center gap-2 border-b border-white/50 px-1 pb-2 text-[11px] font-medium uppercase tracking-wider text-slate-400">
            <span>排名</span>
            <span>用户</span>
            <span className="text-right">点赞得分</span>
          </div>

          {loadingTop && <EmptyState loading />}

          <MessageBanner tone="error" show={!loadingTop && !!topError}>
            {topError || "加载排行榜失败"}
          </MessageBanner>

          {!loadingTop && !topError && items.length === 0 && (
            <EmptyState>今日暂无排行数据</EmptyState>
          )}

          <div className="flex flex-col">
            {!loadingTop &&
              !topError &&
              items.map((item) => (
                <div
                  key={`${item.userId}-${item.rank}`}
                  className="grid grid-cols-[56px_1fr_110px] items-center gap-2 rounded-lg px-1 py-2.5 transition-colors hover:bg-white/50"
                >
                  <span
                    className={cn(
                      "flex size-8 items-center justify-center rounded-full text-xs font-bold",
                      rankBadgeClass(item.rank),
                    )}
                  >
                    {item.rank}
                  </span>
                  <div className="flex min-w-0 items-center gap-2.5">
                    <UserAvatar
                      src={item.avatar}
                      nickname={item.nickname ?? "用户"}
                      className="size-8"
                    />
                    <span className="truncate text-sm font-medium text-slate-700">
                      {item.nickname?.trim() || `用户 ${item.userId}`}
                    </span>
                  </div>
                  <span className="text-right text-sm font-semibold text-slate-800">
                    {formatScore(item.score)}
                  </span>
                </div>
              ))}
          </div>
        </GlassCard>

        {/* 我的排行 */}
        <GlassCard
          delay={0.1}
          className="lg:col-span-4"
          contentClassName="flex flex-col gap-4"
        >
          <SectionLabel>我的排行</SectionLabel>

          {!user && (
            <div className="flex flex-col gap-3">
              <p className="text-sm text-slate-500">登录后查看个人排行</p>
              <Link href="/login?next=/app/leaderboard">
                <Button size="sm" className="w-full">
                  去登录
                </Button>
              </Link>
            </div>
          )}

          {user && loadingMine && <EmptyState loading />}

          <MessageBanner tone="error" show={!!user && !loadingMine && !!mineError}>
            {mineError}
          </MessageBanner>

          {user && !loadingMine && !mineError && myPosition && (
            <div className="flex flex-col gap-4">
              <div className="flex items-center gap-3">
                <UserAvatar
                  src={myPosition.avatar ?? user.avatar}
                  nickname={myPosition.nickname ?? user.nickname}
                  className="size-12 ring-2 ring-white/60"
                />
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-slate-800">
                    {myPosition.nickname || user.nickname || "我"}
                  </p>
                  <p className="text-xs text-slate-400">
                    得分 {formatScore(myPosition.score)}
                  </p>
                </div>
              </div>
              <div className="rounded-xl border border-white/60 bg-white/40 p-4 text-center backdrop-blur-md">
                <p className="text-3xl font-bold text-gradient">
                  {myPosition.rank ? `#${myPosition.rank}` : "—"}
                </p>
                <p className="mt-1 text-xs text-slate-400">
                  {rankTypeLabel(myPosition.rankType)}
                </p>
              </div>
            </div>
          )}
        </GlassCard>
      </div>
    </StudioShell>
  )
}
