"use client"

import { useEffect, useState, useRef, useCallback } from "react"
import { PostCard } from "@/components/ui/post-card"
import { LikeFavBar } from "@/components/ui/like-fav-bar"
import {
  EmptyState,
  MessageBanner,
  PageHeader,
  StatusChip,
  StudioShell,
} from "@/components/ui/studio"
import { knowpostService } from "@/lib/api/knowpost"
import type { FeedItem } from "@/lib/types/knowpost"
import { Sparkles, Loader2 } from "lucide-react"

const PAGE_SIZE = 20

function parseTags(tagJson?: string): string[] {
  if (!tagJson) return []
  try {
    const parsed = JSON.parse(tagJson)
    return Array.isArray(parsed) ? parsed.filter((t) => typeof t === "string") : []
  } catch {
    return []
  }
}

export default function HomePage() {
  const [items, setItems] = useState<FeedItem[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(true)

  const pageRef = useRef(1)
  const loadingMoreRef = useRef(false)
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 首屏加载第 1 页
  useEffect(() => {
    let cancelled = false
    knowpostService
      .feed(1, PAGE_SIZE)
      .then((resp) => {
        if (cancelled) return
        setItems(resp.items ?? [])
        pageRef.current = 1
        setHasMore(resp.hasMore)
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "加载失败")
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  // 加载下一页并追加
  const loadMore = useCallback(() => {
    if (loadingMoreRef.current || !hasMore || loading) return
    loadingMoreRef.current = true
    setLoadingMore(true)
    const nextPage = pageRef.current + 1
    knowpostService
      .feed(nextPage, PAGE_SIZE)
      .then((resp) => {
        setItems((prev) => [...prev, ...(resp.items ?? [])])
        pageRef.current = nextPage
        setHasMore(resp.hasMore)
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : "加载更多失败")
      })
      .finally(() => {
        setLoadingMore(false)
        loadingMoreRef.current = false
      })
  }, [hasMore, loading])

  // 触底哨兵：进入视口（提前 600px 预加载）即拉取下一页
  useEffect(() => {
    const el = sentinelRef.current
    if (!el) return
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) loadMore()
      },
      { rootMargin: "600px 0px" },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [loadMore])

  return (
    <StudioShell>
      <PageHeader
        badge={
          <StatusChip icon={Sparkles} tone="violet">
            知识流
          </StatusChip>
        }
        title="Line"
        subtitle="发现同学们 freshly 分享的知识与灵感"
        chips={
          !loading && items.length > 0 ? (
            <StatusChip>已加载 {items.length} 篇知文</StatusChip>
          ) : null
        }
      />

      <MessageBanner tone="error" show={!!error}>
        {error}
      </MessageBanner>

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {items.map((item) => (
          <PostCard
            key={item.id}
            id={item.id}
            title={item.title}
            summary={item.description ?? ""}
            tags={item.tags ?? []}
            authorTags={parseTags(item.tagJson)}
            teacher={{
              name: item.authorNickname,
              avatarUrl: item.authorAvatar ?? item.authorAvator,
            }}
            coverImage={item.coverImage}
            to={`/app/posts/${item.id}`}
            className="h-full"
            footerExtra={
              <LikeFavBar
                entityId={item.id}
                compact
                initialCounts={{
                  like: item.likeCount ?? 0,
                  fav: item.favoriteCount ?? 0,
                }}
                initialState={{
                  liked: item.liked,
                  faved: item.faved,
                }}
              />
            }
          />
        ))}
      </div>

      {/* 触底哨兵 */}
      <div ref={sentinelRef} className="h-1 w-full" />

      {loading ? (
        <EmptyState loading />
      ) : items.length === 0 ? (
        <EmptyState>暂无内容，快去创作第一篇知文吧</EmptyState>
      ) : loadingMore ? (
        <div className="mt-2 flex items-center justify-center gap-2 py-6 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" />
          正在加载更多…
        </div>
      ) : !hasMore ? (
        <div className="mt-2 py-6 text-center text-sm text-muted-foreground">
          已经到底啦，共 {items.length} 篇知文
        </div>
      ) : null}
    </StudioShell>
  )
}
