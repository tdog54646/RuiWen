"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { relationService } from "@/lib/api/relation"
import { useAuth } from "@/components/auth/auth-context"
import type { ProfileResponse } from "@/lib/types/profile"
import { UserAvatar } from "./user-avatar"
import { Button } from "./button"

type RelationListModalProps = {
  open: boolean
  onClose: () => void
  userId: number
  mode: "following" | "followers"
}

const LIMIT = 20

export function RelationListModal({
  open,
  onClose,
  userId,
  mode,
}: RelationListModalProps) {
  const title = mode === "following" ? "关注列表" : "粉丝列表"
  const { tokens } = useAuth()
  const [profiles, setProfiles] = useState<ProfileResponse[]>([])
  const [offset, setOffset] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(true)

  useEffect(() => {
    if (!open) return
    if (!tokens?.accessToken) {
      setError("请登录后查看列表")
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    const fn =
      mode === "following"
        ? relationService.following
        : relationService.followers
    fn(userId, LIMIT, 0, undefined, tokens.accessToken)
      .then((resp) => {
        if (cancelled) return
        const list = Array.isArray(resp) ? resp : []
        setProfiles(list)
        setOffset(list.length)
        setHasMore(list.length >= LIMIT)
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : "加载失败")
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open, userId, mode, tokens?.accessToken])

  const loadMore = async () => {
    if (loading || !hasMore || !tokens?.accessToken) return
    setLoading(true)
    try {
      const fn =
        mode === "following"
          ? relationService.following
          : relationService.followers
      const resp = await fn(userId, LIMIT, offset, undefined, tokens.accessToken)
      const list = Array.isArray(resp) ? resp : []
      setProfiles((prev) => [...prev, ...list])
      setOffset((prev) => prev + list.length)
      setHasMore(list.length >= LIMIT)
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载失败")
    } finally {
      setLoading(false)
    }
  }

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={onClose}
    >
      <div
        className="flex max-h-[80vh] w-full max-w-lg flex-col rounded-xl bg-background shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b px-4 py-3">
          <span className="font-semibold">{title}</span>
          <button
            className="text-sm text-muted-foreground"
            onClick={onClose}
          >
            关闭
          </button>
        </div>
        <div className="flex-1 overflow-auto p-4">
          {error && (
            <div className="text-sm text-destructive">{error}</div>
          )}
          {profiles.length === 0 && !loading && (
            <div className="py-6 text-center text-sm text-muted-foreground">
              暂无数据
            </div>
          )}
          <div className="flex flex-col gap-2">
            {profiles.map((p) => (
              <Link
                key={p.id}
                href={`/app/profile/${p.id}`}
                className="flex items-center gap-3 rounded-lg border p-2 transition-colors hover:bg-muted/40"
                onClick={onClose}
              >
                <UserAvatar
                  src={p.avatar || undefined}
                  nickname={p.nickname}
                  className="size-8"
                />
                <span className="text-sm">{p.nickname || "用户"}</span>
              </Link>
            ))}
          </div>
        </div>
        <div className="border-t px-4 py-3 text-right">
          <Button
            variant="outline"
            size="sm"
            onClick={loadMore}
            disabled={!hasMore || loading}
          >
            {loading ? "加载中..." : hasMore ? "加载更多" : "没有更多"}
          </Button>
        </div>
      </div>
    </div>
  )
}
