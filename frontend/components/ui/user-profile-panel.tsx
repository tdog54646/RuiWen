"use client"

import type { ReactNode } from "react"
import { LikeFavBar } from "@/components/ui/like-fav-bar"
import { PostCard } from "@/components/ui/post-card"
import { RelationCounters } from "@/components/ui/relation-counters"
import { UserAvatar } from "@/components/ui/user-avatar"
import {
  EmptyState,
  GlassCard,
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

      <GlassCard delay={0.05} contentClassName="flex flex-col gap-5">
        <div className="flex flex-col items-center gap-4 text-center sm:flex-row sm:items-center sm:gap-6 sm:text-left">
          <div className="rounded-full bg-gradient-to-br from-cyan-400/50 via-violet-500/40 to-blue-500/50 p-[3px] shadow-lg shadow-violet-500/20">
            <UserAvatar
              src={profile?.avatar || undefined}
              nickname={displayName}
              className="size-32 rounded-full ring-2 ring-white"
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <span className="text-2xl font-bold text-slate-900">{displayName}</span>
            <div className="flex flex-wrap justify-center gap-2 text-sm text-slate-500 sm:justify-start">
              {tags.length > 0
                ? tags.map((tag) => (
                    <span
                      key={tag}
                      className="rounded-full bg-violet-100/70 px-2 py-0.5 text-xs font-medium text-violet-700"
                    >
                      #{tag}
                    </span>
                  ))
                : <span className="text-xs">未设置标签</span>}
            </div>
          </div>
        </div>

        <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-600">
          {profile?.bio ?? "暂无简介"}
        </p>

        {profile?.id ? <RelationCounters userId={profile.id} /> : null}
      </GlassCard>

      <div className="flex flex-col gap-3">
        <div className="flex items-baseline justify-between">
          <h2 className="text-lg font-semibold text-slate-800">{postsTitle}</h2>
          {!loading && items.length > 0 && (
            <span className="text-xs text-slate-400">共 {items.length} 篇</span>
          )}
        </div>

        <MessageBanner tone="error" show={!!error}>
          {error}
        </MessageBanner>

        <div className="columns-1 gap-4 sm:columns-2 lg:columns-3">
          {items.map((item) => (
            <div key={item.id} className="mb-4 break-inside-avoid">
              <PostCard
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
        </div>

        {loading ? (
          <EmptyState loading />
        ) : items.length === 0 ? (
          <EmptyState>{emptyText}</EmptyState>
        ) : null}
      </div>
    </StudioShell>
  )
}
