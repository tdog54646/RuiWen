"use client"

import Link from "next/link"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useAuth } from "@/components/auth/auth-context"
import { Button } from "@/components/ui/button"
import { UserAvatar } from "@/components/ui/user-avatar"
import {
  EmptyState,
  MessageBanner,
  PageHeader,
  StudioShell,
} from "@/components/ui/studio"
import { leaderboardService } from "@/lib/api/leaderboard"
import type {
  LeaderboardTopItem,
  LeaderboardUserPosition,
  RankType,
} from "@/lib/types/leaderboard"
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
  if (rank === 1) return "text-[#8a6a24]"
  if (rank === 2) return "text-[#68706a]"
  if (rank === 3) return "text-[#885d3c]"
  return "text-[#8b8f8a]"
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
        title="排行榜"
        subtitle={`每日点赞得分 Top 20 · ${date.slice(0, 4)}.${date.slice(4, 6)}.${date.slice(6, 8)}`}
      />

      <div className="grid grid-cols-1 gap-6 pt-2 lg:grid-cols-[minmax(0,1fr)_21rem]">
        {/* Top 20 榜单 */}
        <section className="flex flex-col rounded-2xl bg-[#efefe9] p-4 md:p-6">
          <div className="mb-4 flex items-end justify-between px-1 py-2">
            <h2 className="font-display text-3xl tracking-[-0.04em] text-[#1d211f]">今日 Top 20</h2>
            <span className="text-sm text-[#777b76]">点赞得分</span>
          </div>

          <div className="grid grid-cols-[64px_1fr_100px] items-center gap-3 rounded-lg bg-[#e3e3dd] px-4 py-3 text-sm font-semibold text-[#606560]">
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

          <div className="mt-2 flex flex-col gap-2">
            {!loadingTop &&
              !topError &&
              items.map((item) => (
                <div
                  key={`${item.userId}-${item.rank}`}
                  className="grid grid-cols-[64px_1fr_100px] items-center gap-3 rounded-xl bg-[#fbfbf8] px-4 py-4 shadow-[0_10px_25px_-24px_rgba(29,33,31,0.55)] transition-transform hover:-translate-y-0.5"
                >
                  <span
                    className={cn(
                      "font-display text-2xl tabular-nums",
                      rankBadgeClass(item.rank),
                    )}
                  >
                    {item.rank}
                  </span>
                  <div className="flex min-w-0 items-center gap-2.5">
                    <UserAvatar
                      src={item.avatar}
                      nickname={item.nickname ?? "用户"}
                      className="size-9 rounded-lg"
                    />
                    <span className="truncate text-sm font-semibold text-[#414642]">
                      {item.nickname?.trim() || `用户 ${item.userId}`}
                    </span>
                  </div>
                  <span className="text-right font-mono text-sm font-semibold tabular-nums text-[#252a27]">
                    {formatScore(item.score)}
                  </span>
                </div>
              ))}
          </div>
        </section>

        {/* 我的排行 */}
        <aside className="h-fit rounded-2xl bg-[#fbfbf8] p-6 shadow-[0_20px_45px_-38px_rgba(29,33,31,0.55)] ring-1 ring-[#deded8] lg:sticky lg:top-28">
          <h2 className="font-display text-3xl tracking-[-0.035em] text-[#1d211f]">我的排行</h2>

          {!user && (
            <div className="mt-6 flex flex-col gap-4">
              <p className="text-sm leading-6 text-[#70746f]">登录后查看自己的今日得分与名次。</p>
              <Link href="/login?next=/app/leaderboard">
                <Button size="sm" className="w-full rounded-lg bg-[#1d211f] text-white hover:bg-[#2f5d50]">
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
            <div className="mt-6 flex flex-col gap-5">
              <div className="flex items-center gap-3.5">
                <UserAvatar
                  src={myPosition.avatar ?? user.avatar}
                  nickname={myPosition.nickname ?? user.nickname}
                  className="size-12 rounded-xl ring-2 ring-[#deded8]"
                />
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-[#252a27]">
                    {myPosition.nickname || user.nickname || "我"}
                  </p>
                  <p className="mt-1 text-sm text-[#777b76]">
                    得分 {formatScore(myPosition.score)}
                  </p>
                </div>
              </div>
              <div className="rounded-xl bg-[#efefe9] p-5">
                <p className="font-display text-5xl font-medium tracking-[-0.05em] text-[#2f5d50]">
                  {myPosition.rank ? `#${myPosition.rank}` : "—"}
                </p>
                <p className="mt-2 text-sm text-[#70746f]">
                  {rankTypeLabel(myPosition.rankType)}
                </p>
              </div>
            </div>
          )}
        </aside>
      </div>
    </StudioShell>
  )
}
