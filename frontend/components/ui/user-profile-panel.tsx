"use client"

import type { ReactNode } from "react"
import { LikeFavBar } from "@/components/ui/like-fav-bar"
import { PostCard } from "@/components/ui/post-card"
import { RelationCounters } from "@/components/ui/relation-counters"
import { UserAvatar } from "@/components/ui/user-avatar"
import type { FeedItem } from "@/lib/types/knowpost"

function parseTags(tagJson?: string | null): string[] {
  if (!tagJson) return []
  try {
    const parsed = JSON.parse(tagJson)
    if (!Array.isArray(parsed)) return []
    const seen = new Set<string>()
    return parsed.filter((tag) => typeof tag === "string" && !seen.has(tag) && seen.add(tag))
  } catch {
    return []
  }
}

type UserProfileSummary = {
  id?: number | null
  nickname?: string | null
  avatar?: string | null
  bio?: string | null
  tagJson?: string | null
  phone?: string | null
  email?: string | null
}

type UserProfilePanelProps = {
  pageTitle: string
  pageSubtitle: string
  profile: UserProfileSummary | null
  headerAction?: ReactNode
  postsTitle?: string
  items: FeedItem[]
  loading: boolean
  error?: string | null
  emptyText: string
  editable?: boolean
  onPostChanged?: (
    itemId: string,
    action: "top" | "visibility" | "delete",
    payload?: unknown,
  ) => void
}

export function UserProfilePanel({
  pageTitle,
  pageSubtitle,
  profile,
  headerAction,
  postsTitle = "知文列表",
  items,
  loading,
  error,
  emptyText,
  editable = false,
  onPostChanged,
}: UserProfilePanelProps) {
  const displayName = profile?.nickname ?? profile?.phone ?? profile?.email ?? "用户"
  const tags = parseTags(profile?.tagJson)

  return (
    <div className="flex flex-col gap-6 rounded-2xl bg-background/90 p-6 shadow-sm">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight">{pageTitle}</h1>
        <p className="text-sm text-muted-foreground">{pageSubtitle}</p>
      </div>

      <div className="flex flex-col gap-6">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">个人信息</h2>
          {headerAction}
        </div>

        <div className="flex items-center gap-6">
          <UserAvatar
            src={profile?.avatar || undefined}
            nickname={displayName}
            size="lg"
            className="size-20"
          />
          <div className="flex flex-col gap-1">
            <span className="text-xl font-bold">{displayName}</span>
            <div className="flex flex-wrap gap-2 text-sm text-muted-foreground">
              {tags.length > 0
                ? tags.map((tag) => <span key={tag}>{tag}</span>)
                : <span>未设置标签</span>}
            </div>
          </div>
        </div>

        <p className="whitespace-pre-wrap text-sm text-muted-foreground">
          {profile?.bio ?? "暂无简介"}
        </p>

        {profile?.id ? <RelationCounters userId={profile.id} /> : null}

        <div className="border-t pt-4">
          <h2 className="mb-4 text-lg font-semibold">{postsTitle}</h2>
          {error ? (
            <div className="mb-4 text-sm text-destructive">{error}</div>
          ) : null}
          <div className="columns-1 gap-6 sm:columns-2 lg:columns-3">
            {items.map((item) => (
              <div key={item.id} className="mb-6 break-inside-avoid">
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
                  editable={editable}
                  onChanged={(action, payload) => onPostChanged?.(item.id, action, payload)}
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
            {loading ? (
              <div className="text-center text-sm text-muted-foreground">
                加载中…
              </div>
            ) : null}
            {!loading && items.length === 0 ? (
              <div className="text-center text-sm text-muted-foreground">
                {emptyText}
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  )
}
