"use client"

import { useEffect, useState } from "react"
import { PostCard } from "@/components/ui/post-card"
import { LikeFavBar } from "@/components/ui/like-fav-bar"
import { knowpostService } from "@/lib/api/knowpost"
import type { FeedItem } from "@/lib/types/knowpost"

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
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    knowpostService
      .feed(1, 20)
      .then((resp) => {
        if (!cancelled) setItems(resp.items ?? [])
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

  return (
    <div className="rounded-2xl bg-background/90 p-6 shadow-sm">
      <h1 className="mb-5 text-2xl font-bold tracking-tight">Line</h1>
      {error && <div className="mb-4 text-sm text-destructive">{error}</div>}
      <div className="columns-1 gap-6 sm:columns-2 lg:columns-3 xl:columns-4">
        {items.map((item) => (
          <div key={item.id} className="mb-6 break-inside-avoid">
            <PostCard
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
          </div>
        ))}
        {loading && (
          <div className="mb-6 break-inside-avoid text-center text-sm text-muted-foreground">
            加载中…
          </div>
        )}
        {!loading && items.length === 0 && (
          <div className="mb-6 break-inside-avoid text-center text-sm text-muted-foreground">
            暂无内容
          </div>
        )}
      </div>
    </div>
  )
}
