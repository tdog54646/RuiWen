"use client"

import { useEffect, useState, useRef, useCallback } from "react"
import Link from "next/link"
import { PostCard } from "@/components/ui/post-card"
import { LikeFavBar } from "@/components/ui/like-fav-bar"
import { MessageBanner, StudioShell } from "@/components/ui/studio"
import { knowpostService } from "@/lib/api/knowpost"
import type { FeedItem } from "@/lib/types/knowpost"
import { ArrowUpRight, BookOpenText, Loader2, PenLine } from "lucide-react"

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

function FeedSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-x-6 gap-y-10 sm:grid-cols-2 lg:grid-cols-12">
      {Array.from({ length: 7 }).map((_, index) => (
        <div
          key={index}
          className={
            index === 0
              ? "sm:col-span-2 lg:col-span-8"
              : "lg:col-span-4"
          }
        >
          <div className="overflow-hidden rounded-[1.25rem] border border-[#deded8] bg-[#fbfbf8]">
            <div
              className={
                index === 0
                  ? "studio-shimmer aspect-[16/8] w-full lg:aspect-[16/7]"
                  : "studio-shimmer aspect-[4/3] w-full"
              }
            />
            <div className="space-y-3 p-5">
              <div className="studio-shimmer h-3 w-20 rounded-sm" />
              <div className="studio-shimmer h-6 w-4/5 rounded-sm" />
              <div className="studio-shimmer h-4 w-full rounded-sm" />
              <div className="studio-shimmer h-4 w-2/3 rounded-sm" />
            </div>
          </div>
        </div>
      ))}
    </div>
  )
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
    <StudioShell className="pb-8">
      <section className="grid gap-12 pb-14 pt-8 md:grid-cols-[minmax(0,1.6fr)_minmax(16rem,0.65fr)] md:items-end md:gap-16 md:pb-20 md:pt-14">
        <div>
          <h1 className="font-display max-w-[820px] text-balance text-[clamp(3rem,7vw,6.6rem)] font-medium leading-[0.94] tracking-[-0.075em] text-[#1d211f]">
            把好问题，
            <span className="block text-[#2f5d50]">变成共享的知识。</span>
          </h1>
          <p className="mt-7 max-w-[38rem] text-pretty text-[15px] leading-7 text-[#4f5953] md:mt-9 md:text-base">
            阅读同学们正在研究、实践和思考的内容。少一点信息噪音，多一点值得保存的观点。
          </p>
          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Link
              href="/app/posts/create"
              className="group inline-flex h-11 items-center gap-2 rounded-lg bg-[#1d211f] px-5 text-sm font-semibold text-white transition-all duration-200 hover:-translate-y-0.5 hover:bg-[#2f5d50] active:translate-y-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50] focus-visible:ring-offset-4 focus-visible:ring-offset-[#f7f7f3]"
            >
              <PenLine className="size-4" />
              写下新知
              <ArrowUpRight className="size-3.5 transition-transform duration-200 group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
            </Link>
            <Link
              href="/app/search"
              className="inline-flex h-11 items-center gap-2 rounded-lg px-4 text-sm font-semibold text-[#343936] transition-colors hover:bg-[#ecece6] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
            >
              浏览主题
            </Link>
          </div>
        </div>

        <aside className="flex flex-col justify-between md:mb-1 md:pl-6">
          <BookOpenText className="mb-7 size-6 text-[#2f5d50]" strokeWidth={1.6} />
          <p className="font-display text-balance text-2xl leading-[1.3] tracking-[-0.035em] text-[#2d312e]">
            每一次认真记录，都可能成为另一个人的起点。
          </p>
          {!loading && items.length > 0 && (
            <p className="mt-8 text-sm font-medium text-[#777b76]">本次更新 {items.length} 篇</p>
          )}
        </aside>
      </section>

      <MessageBanner tone="error" show={!!error}>
        {error}
      </MessageBanner>

      <section aria-labelledby="latest-heading" className="pt-12 md:pt-16">
        <div className="mb-8 flex items-end justify-between gap-6 md:mb-10">
          <div>
            <h2
              id="latest-heading"
              className="font-display text-3xl font-medium tracking-[-0.045em] text-[#1d211f] md:text-4xl"
            >
              最近发布
            </h2>
          </div>
        </div>

        {loading ? (
          <FeedSkeleton />
        ) : items.length === 0 ? (
          <div className="flex min-h-72 flex-col items-start justify-center rounded-2xl bg-[#efefe9] px-8 py-14">
            <p className="font-display text-3xl tracking-[-0.04em] text-[#2c312e]">
              这里还没有内容
            </p>
            <p className="mt-3 max-w-md text-sm leading-6 text-[#737772]">
              成为第一个分享问题、方法或实践记录的人。
            </p>
            <Link
              href="/app/posts/create"
              className="mt-7 inline-flex items-center gap-2 text-sm font-semibold text-[#2f5d50] underline decoration-[#9bad9f] underline-offset-4 transition-colors hover:text-[#1d211f] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
            >
              创作第一篇知文
              <ArrowUpRight className="size-4" />
            </Link>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-x-6 gap-y-10 sm:grid-cols-2 lg:grid-cols-12">
            {items.map((item, index) => (
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
                featured={index === 0}
                className={
                  index === 0
                    ? "sm:col-span-2 lg:col-span-8"
                    : "lg:col-span-4"
                }
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
        )}
      </section>

      {/* 触底哨兵 */}
      <div ref={sentinelRef} className="h-1 w-full" />

      {!loading && items.length > 0 && loadingMore ? (
        <div className="mt-8 flex items-center justify-center gap-2 py-8 text-sm text-[#777b76]">
          <Loader2 className="size-4 animate-spin" />
          正在加载更多…
        </div>
      ) : items.length > 0 && !hasMore ? (
        <div className="mt-10 text-center text-sm font-medium text-[#656d68]">
          本次已读完，共 {items.length} 篇
        </div>
      ) : null}
    </StudioShell>
  )
}
