"use client"

import Link from "next/link"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useAuth } from "@/components/auth/auth-context"
import { Button } from "@/components/ui/button"
import { UserAvatar } from "@/components/ui/user-avatar"
import { leaderboardService } from "@/lib/api/leaderboard"
import type {
  LeaderboardTopItem,
  LeaderboardUserPosition,
  RankType,
} from "@/lib/types/leaderboard"

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
    <div className="flex flex-col gap-6 rounded-2xl bg-background/90 p-6 shadow-sm">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight">排行榜</h1>
        <p className="text-sm text-muted-foreground">
          今日点赞榜 Top20（{date}）
        </p>
      </div>

      <section className="rounded-xl border">
        <div className="grid grid-cols-[80px_1fr_120px] border-b bg-muted/40 px-4 py-3 text-sm font-medium">
          <span>排名</span>
          <span>用户</span>
          <span className="text-right">得分</span>
        </div>

        {loadingTop && (
          <div className="px-4 py-6 text-sm text-muted-foreground">加载中…</div>
        )}

        {!loadingTop && topError && (
          <div className="px-4 py-6 text-sm text-destructive">
            {topError || "加载排行榜失败"}
          </div>
        )}

        {!loadingTop && !topError && items.length === 0 && (
          <div className="px-4 py-6 text-sm text-muted-foreground">今日暂无排行数据</div>
        )}

        {!loadingTop &&
          !topError &&
          items.map((item) => (
            <div
              key={`${item.userId}-${item.rank}`}
              className="grid grid-cols-[80px_1fr_120px] items-center border-b px-4 py-3 last:border-b-0"
            >
              <span className="text-sm font-semibold">#{item.rank}</span>
              <div className="flex items-center gap-3">
                <UserAvatar src={item.avatar} nickname={item.nickname ?? "用户"} />
                <span className="truncate text-sm">
                  {item.nickname?.trim() || `用户 ${item.userId}`}
                </span>
              </div>
              <span className="text-right text-sm font-medium">
                {formatScore(item.score)}
              </span>
            </div>
          ))}
      </section>

      <section className="rounded-xl border p-4">
        <h2 className="mb-3 text-lg font-semibold">我的排行</h2>

        {!user && (
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">登录后查看个人排行</p>
            <Link href="/login?next=/app/leaderboard">
              <Button size="sm">去登录</Button>
            </Link>
          </div>
        )}

        {user && loadingMine && (
          <p className="text-sm text-muted-foreground">正在加载个人排行…</p>
        )}

        {user && !loadingMine && mineError && (
          <p className="text-sm text-destructive">{mineError}</p>
        )}

        {user && !loadingMine && !mineError && myPosition && (
          <div className="flex items-center justify-between gap-4">
            <div className="flex min-w-0 items-center gap-3">
              <UserAvatar
                src={myPosition.avatar ?? user.avatar}
                nickname={myPosition.nickname ?? user.nickname}
              />
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">
                  {myPosition.nickname || user.nickname || "我"}
                </p>
                <p className="text-xs text-muted-foreground">
                  得分 {formatScore(myPosition.score)}
                </p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-sm font-semibold">
                {myPosition.rank ? `#${myPosition.rank}` : "-"}
              </p>
              <p className="text-xs text-muted-foreground">
                {rankTypeLabel(myPosition.rankType)}
              </p>
            </div>
          </div>
        )}
      </section>
    </div>
  )
}
