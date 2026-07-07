"use client"

import { useRef, useState } from "react"
import { SearchBar } from "@/components/ui/search-bar"
import { PostCard } from "@/components/ui/post-card"
import { LikeFavBar } from "@/components/ui/like-fav-bar"
import {
  EmptyState,
  GlassCard,
  PageHeader,
  StatusChip,
  StudioShell,
} from "@/components/ui/studio"
import { searchService } from "@/lib/api/search"
import { useAuth } from "@/components/auth/auth-context"
import type { FeedItem } from "@/lib/types/knowpost"
import { Button } from "@/components/ui/button"
import { Search } from "lucide-react"

function parseTags(tagJson?: string): string[] {
  if (!tagJson) return []
  try {
    const parsed = JSON.parse(tagJson)
    return Array.isArray(parsed) ? parsed.filter((t) => typeof t === "string") : []
  } catch {
    return []
  }
}

export default function SearchPage() {
  const [q, setQ] = useState("")
  const [items, setItems] = useState<FeedItem[]>([])
  const [after, setAfter] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [suggestions, setSuggestions] = useState<string[]>([])
  const [suggestLoading, setSuggestLoading] = useState(false)
  const debounceRef = useRef<number | null>(null)
  const { user } = useAuth()
  const [showLoginHint, setShowLoginHint] = useState(false)

  const executeSearch = async (keyword: string) => {
    const text = keyword.trim()
    if (!text) return
    if (!user) setShowLoginHint(true)
    setQ(text)
    setLoading(true)
    try {
      const resp = await searchService.query({ q: text, size: 20 })
      setItems(resp.items ?? [])
      setAfter(resp.nextAfter ?? null)
      setHasMore(!!resp.hasMore)
    } catch {
      setItems([])
      setAfter(null)
      setHasMore(false)
    } finally {
      setLoading(false)
    }
  }

  const loadMore = async () => {
    if (!q.trim() || !after) return
    setLoading(true)
    try {
      const resp = await searchService.query({ q: q.trim(), size: 20, after })
      setItems((prev) => [...prev, ...(resp.items ?? [])])
      setAfter(resp.nextAfter ?? null)
      setHasMore(!!resp.hasMore)
    } catch {
      // keep existing
    } finally {
      setLoading(false)
    }
  }

  return (
    <StudioShell>
      <PageHeader
        badge={
          <StatusChip icon={Search} tone="cyan">
            知识检索
          </StatusChip>
        }
        title="搜索你想学习的知识"
        subtitle="从提示词或你的历史记录开始探索，连接灵感与成长"
      />

      <GlassCard delay={0.05} disableHover>
        <SearchBar
          placeholder="搜索你想学习的知识..."
          value={q}
          suggestions={suggestions}
          suggestLoading={suggestLoading}
          onSuggestionClick={(s) => executeSearch(s)}
          onChange={(val) => {
            setQ(val)
            if (debounceRef.current) window.clearTimeout(debounceRef.current)
            debounceRef.current = window.setTimeout(async () => {
              if (!val.trim()) {
                setSuggestions([])
                return
              }
              try {
                setSuggestLoading(true)
                const resp = await searchService.suggest(val.trim(), 10)
                setSuggestions(resp.items ?? [])
              } catch {
                setSuggestions([])
              } finally {
                setSuggestLoading(false)
              }
            }, 300)
          }}
          onSubmit={() => executeSearch(q)}
        />
      </GlassCard>

      {showLoginHint && !user && (
        <div className="rounded-xl border border-amber-200/60 bg-amber-50/70 px-4 py-2.5 text-sm text-amber-700 backdrop-blur-md">
          当前为未登录状态，登录后可获得更完整的推荐与学习记录。
        </div>
      )}

      <div className="flex items-baseline justify-between">
        <h2 className="text-lg font-semibold text-slate-800">搜索结果</h2>
        <span className="text-xs text-slate-400">
          {loading
            ? "加载中…"
            : items.length
              ? `共 ${items.length} 条（可能有更多）`
              : "请输入关键词后搜索"}
        </span>
      </div>

      <div className="columns-1 gap-4 sm:columns-2 lg:columns-3 xl:columns-4">
        {items.map((item) => (
          <div key={item.id} className="mb-4 break-inside-avoid">
            <PostCard
              id={item.id}
              title={item.title}
              summary={item.description ?? ""}
              tags={item.tags ?? []}
              isTop={item.isTop}
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
                  initialState={{ liked: item.liked, faved: item.faved }}
                />
              }
            />
          </div>
        ))}
      </div>

      {loading && items.length === 0 ? (
        <EmptyState loading />
      ) : !loading && q.trim() && items.length === 0 ? (
        <EmptyState>没有找到相关知文，换个关键词试试</EmptyState>
      ) : null}

      {hasMore && (
        <div className="flex justify-center pt-2">
          <Button
            onClick={loadMore}
            disabled={loading}
            variant="outline"
            className="border-white/60 bg-white/60 backdrop-blur-md"
          >
            {loading ? "加载中…" : "加载更多"}
          </Button>
        </div>
      )}
    </StudioShell>
  )
}
