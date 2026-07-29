"use client"

import type { ReactNode } from "react"
import { LikeFavBar } from "@/components/ui/like-fav-bar"
import { PostCard } from "@/components/ui/post-card"
import { RelationCounters } from "@/components/ui/relation-counters"
import { UserAvatar } from "@/components/ui/user-avatar"
import {
  EmptyState,
  MessageBanner,
  PageHeader,
  StudioShell,
} from "@/components/ui/studio"
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
    <StudioShell>
      <PageHeader
        title={pageTitle}
        subtitle={pageSubtitle}
        chips={headerAction}
      />

      <section className="grid gap-8 rounded-2xl bg-[#fbfbf8] p-6 shadow-[0_24px_55px_-44px_rgba(29,33,31,0.55)] ring-1 ring-[#deded8] md:grid-cols-[10rem_minmax(0,1fr)_auto] md:items-center md:gap-10 md:p-8">
        <div className="w-fit rounded-xl bg-[#ecece6] p-1.5">
          <UserAvatar
            src={profile?.avatar || undefined}
            nickname={displayName}
            className="size-36 rounded-none"
          />
        </div>
        <div className="min-w-0">
          <span className="font-display block truncate text-4xl font-medium tracking-[-0.045em] text-[#1d211f]">
            {displayName}
          </span>
          <div className="mt-4 flex flex-wrap gap-2 text-sm text-[#656a65]">
            {tags.length > 0
              ? tags.map((tag) => (
                  <span
                    key={tag}
                    className="rounded-md bg-[#ecece6] px-2 py-1 text-xs font-medium text-[#555a56]"
                  >
                    #{tag}
                  </span>
                ))
              : <span className="text-sm text-[#777b76]">未设置标签</span>}
          </div>
          <p className="mt-5 max-w-2xl whitespace-pre-wrap text-sm leading-7 text-[#686d68]">
            {profile?.bio ?? "暂无简介"}
          </p>
        </div>

        <div className="rounded-xl bg-[#efefe9] p-5">
          {profile?.id ? <RelationCounters userId={profile.id} /> : null}
        </div>
      </section>

      <section className="flex flex-col gap-5 pt-5">
        <div className="flex items-end justify-between pt-3">
          <div>
            <h2 className="font-display text-3xl font-medium tracking-[-0.04em] text-[#1d211f]">{postsTitle}</h2>
          </div>
          {!loading && items.length > 0 && (
            <span className="text-xs tabular-nums text-[#858984]">共 {items.length} 篇</span>
          )}
        </div>

        <MessageBanner tone="error" show={!!error}>
          {error}
        </MessageBanner>

        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-12">
          {items.map((item, index) => (
              <PostCard
                key={item.id}
                id={item.id}
                title={item.title}
                summary={item.description ?? ""}
                tags={item.tags ?? []}
                isTop={item.isTop}
                visible={item.visible}
                authorTags={parseTags(item.tagJson)}
                teacher={{
                  name: item.authorNickname,
                  avatarUrl: item.authorAvatar ?? item.authorAvator,
                }}
                coverImage={item.coverImage}
                to={`/app/posts/${item.id}`}
                featured={index === 0}
                className={index === 0 ? "sm:col-span-2 lg:col-span-8" : "lg:col-span-4"}
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
          ))}
        </div>

        {loading ? (
          <EmptyState loading />
        ) : items.length === 0 ? (
          <EmptyState>{emptyText}</EmptyState>
        ) : null}
      </section>
    </StudioShell>
  )
}
