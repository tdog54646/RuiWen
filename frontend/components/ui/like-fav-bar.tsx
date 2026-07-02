"use client"

import { useEffect, useState } from "react"
import { useRouter, usePathname } from "next/navigation"
import { Heart, Bookmark } from "lucide-react"
import { useAuth } from "@/components/auth/auth-context"
import { knowpostService } from "@/lib/api/knowpost"
import { cn } from "@/lib/utils"

type LikeFavBarProps = {
  entityId: string
  entityType?: string
  initialCounts?: { like: number; fav: number }
  initialState?: { liked?: boolean; faved?: boolean }
  fetchCounts?: boolean
  compact?: boolean
  className?: string
}

export function LikeFavBar({
  entityId,
  entityType = "knowpost",
  initialCounts,
  initialState,
  fetchCounts = false,
  compact = false,
  className,
}: LikeFavBarProps) {
  const { tokens } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const iconSize = compact ? 16 : 18

  const [likeCount, setLikeCount] = useState(initialCounts?.like ?? 0)
  const [favCount, setFavCount] = useState(initialCounts?.fav ?? 0)
  const [liked, setLiked] = useState(initialState?.liked ?? false)
  const [faved, setFaved] = useState(initialState?.faved ?? false)
  const [loadingLike, setLoadingLike] = useState(false)
  const [loadingFav, setLoadingFav] = useState(false)

  useEffect(() => {
    if (!fetchCounts || !tokens?.accessToken) return
    let cancelled = false
    knowpostService
      .counters(entityId, tokens.accessToken, entityType)
      .then((resp) => {
        if (cancelled) return
        setLikeCount(resp.counts?.like ?? 0)
        setFavCount(resp.counts?.fav ?? 0)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [entityId, entityType, tokens?.accessToken, fetchCounts])

  useEffect(() => {
    if (typeof initialState?.liked !== "undefined") setLiked(!!initialState.liked)
    if (typeof initialState?.faved !== "undefined") setFaved(!!initialState.faved)
  }, [initialState?.liked, initialState?.faved])

  const mustLogin = () => {
    router.push(`/login?next=${encodeURIComponent(pathname)}`)
  }

  const onLikeClick = async (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (!tokens?.accessToken) return mustLogin()
    if (loadingLike) return
    setLoadingLike(true)
    try {
      if (!liked) {
        const resp = await knowpostService.like(entityId, tokens.accessToken, entityType)
        setLiked(resp.liked)
        if (resp.changed && resp.liked) setLikeCount((c) => c + 1)
      } else {
        const resp = await knowpostService.unlike(entityId, tokens.accessToken, entityType)
        setLiked(resp.liked)
        if (resp.changed && !resp.liked) setLikeCount((c) => Math.max(0, c - 1))
      }
    } catch {
      // ignore
    } finally {
      setLoadingLike(false)
    }
  }

  const onFavClick = async (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (!tokens?.accessToken) return mustLogin()
    if (loadingFav) return
    setLoadingFav(true)
    try {
      if (!faved) {
        const resp = await knowpostService.fav(entityId, tokens.accessToken, entityType)
        setFaved(resp.faved)
        if (resp.changed && resp.faved) setFavCount((c) => c + 1)
      } else {
        const resp = await knowpostService.unfav(entityId, tokens.accessToken, entityType)
        setFaved(resp.faved)
        if (resp.changed && !resp.faved) setFavCount((c) => Math.max(0, c - 1))
      }
    } catch {
      // ignore
    } finally {
      setLoadingFav(false)
    }
  }

  return (
    <div className={cn("inline-flex items-center gap-3 text-sm text-muted-foreground", className)}>
      <button
        type="button"
        className={cn(
          "inline-flex items-center gap-1 transition-colors",
          liked && "text-red-500",
          loadingLike && "pointer-events-none opacity-60",
        )}
        onClick={onLikeClick}
        aria-pressed={liked}
        aria-label={liked ? "取消点赞" : "点赞"}
      >
        <Heart className={cn("transition-colors", liked && "fill-current")} width={iconSize} height={iconSize} />
        <span>{likeCount}</span>
      </button>
      <button
        type="button"
        className={cn(
          "inline-flex items-center gap-1 transition-colors",
          faved && "text-primary",
          loadingFav && "pointer-events-none opacity-60",
        )}
        onClick={onFavClick}
        aria-pressed={faved}
        aria-label={faved ? "取消收藏" : "收藏"}
      >
        <Bookmark className={cn("transition-colors", faved && "fill-current")} width={iconSize} height={iconSize} />
        <span>{favCount}</span>
      </button>
    </div>
  )
}
