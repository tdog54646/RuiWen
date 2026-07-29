"use client"

import { useRef, useState } from "react"
import { SearchBar } from "@/components/ui/search-bar"
import { PostCard } from "@/components/ui/post-card"
import { LikeFavBar } from "@/components/ui/like-fav-bar"
import {
  EmptyState,
  MessageBanner,
  PageHeader,
  StudioShell,
} from "@/components/ui/studio"
import { searchService } from "@/lib/api/search"
import { useAuth } from "@/components/auth/auth-context"
import type { FeedItem } from "@/lib/types/knowpost"
import { Button } from "@/components/ui/button"

function SearchSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-12">
      {Array.from({ length: 6 }).map((_, index) => (
        <div
          key={index}
          className={index === 0 ? "sm:col-span-2 lg:col-span-8" : "lg:col-span-4"}
        >
          <div className="overflow-hidden rounded-xl border border-[#deded8] bg-[#fbfbf8]">
            <div className={index === 0 ? "studio-shimmer aspect-[16/7]" : "studio-shimmer aspect-[4/3]"} />
            <div className="space-y-3 p-5">
              <div className="studio-shimmer h-3 w-20 rounded-sm" />
              <div className="studio-shimmer h-6 w-4/5 rounded-sm" />
              <div className="studio-shimmer h-4 w-full rounded-sm" />
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

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
  const [error, setError] = useState<string | null>(null)
  const debounceRef = useRef<number | null>(null)
  const { user } = useAuth()
  const [showLoginHint, setShowLoginHint] = useState(false)

  const executeSearch = async (keyword: string) => {
    const text = keyword.trim()
    if (!text) return
    if (!user) setShowLoginHint(true)
    setQ(text)
    setError(null)
    setLoading(true)
    try {
      const resp = await searchService.query({ q: text, size: 20 })
      setItems(resp.items ?? [])
      setAfter(resp.nextAfter ?? null)
      setHasMore(!!resp.hasMore)
    } catch (err) {
      setItems([])
      setAfter(null)
      setHasMore(false)
      setError(err instanceof Error ? err.message : "搜索失败，请稍后重试")
    } finally {
      setLoading(false)
    }
  }

  const loadMore = async () => {
    if (!q.trim() || !after) return
    setLoading(true)
    setError(null)
    try {
      const resp = await searchService.query({ q: q.trim(), size: 20, after })
      setItems((prev) => [...prev, ...(resp.items ?? [])])
      setAfter(resp.nextAfter ?? null)
      setHasMore(!!resp.hasMore)
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载更多失败")
    } finally {
      setLoading(false)
    }
  }

  return (
    <StudioShell>
      <PageHeader
        title="搜索你想学习的知识"
        subtitle="输入主题、问题或关键词，从同学们的公开知文中找到可靠线索。"
      />

      <section className="py-4">
        <SearchBar
            placeholder="输入主题、问题或关键词"
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
      </section>

      {showLoginHint && !user && (
        <div className="border-l-2 border-[#8a7345] bg-[#f3efe4] px-4 py-3 text-sm text-[#6f5b34]">
          当前为未登录状态，登录后可获得更完整的推荐与学习记录。
        </div>
      )}

      <MessageBanner tone="error" show={!!error}>
        {error}
      </MessageBanner>

      <div className="flex items-end justify-between pt-5">
        <div>
          <h2 className="font-display text-3xl font-medium tracking-[-0.04em] text-[#1d211f]">搜索结果</h2>
        </div>
        <span className="text-sm text-[#858984]">
          {loading
            ? "加载中…"
            : items.length
              ? `共 ${items.length} 条（可能有更多）`
              : "请输入关键词后搜索"}
        </span>
      </div>

      {loading && items.length === 0 ? (
        <SearchSkeleton />
      ) : (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-12">
          {items.map((item, index) => (
            <PostCard
              key={item.id}
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
              featured={index === 0}
              className={index === 0 ? "sm:col-span-2 lg:col-span-8" : "lg:col-span-4"}
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
          ))}
        </div>
      )}

      {!loading && q.trim() && items.length === 0 ? (
        <EmptyState className="rounded-2xl bg-[#efefe9] py-20 text-[#6f746f]">
          没有找到相关知文，换一个更具体的关键词试试。
        </EmptyState>
      ) : null}

      {hasMore && (
        <div className="flex justify-center pt-2">
          <Button
            onClick={loadMore}
            disabled={loading}
            variant="outline"
            className="rounded-lg border-[#cfd1ca] bg-transparent px-6 hover:bg-[#ecece6]"
          >
            {loading ? "加载中…" : "加载更多"}
          </Button>
        </div>
      )}
    </StudioShell>
  )
}
