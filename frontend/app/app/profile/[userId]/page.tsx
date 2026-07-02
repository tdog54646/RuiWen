"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { useParams } from "next/navigation"
import { useAuth } from "@/components/auth/auth-context"
import { Button } from "@/components/ui/button"
import { FollowButton } from "@/components/ui/follow-button"
import { UserProfilePanel } from "@/components/ui/user-profile-panel"
import { authService } from "@/lib/api/auth"
import { knowpostService } from "@/lib/api/knowpost"
import type { PublicUserProfile } from "@/lib/types/auth"
import type { FeedItem } from "@/lib/types/knowpost"

const PAGE_SIZE = 20

export default function UserProfilePage() {
  const params = useParams<{ userId: string }>()
  const rawUserId = params.userId
  const userId = Number(rawUserId)
  const { user, tokens, isLoading: authLoading } = useAuth()
  const [profile, setProfile] = useState<PublicUserProfile | null>(null)
  const [items, setItems] = useState<FeedItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isOwnProfile = Number.isFinite(userId) && !!user?.id && user.id === userId

  const reload = useCallback(async () => {
    if (authLoading) return

    if (!Number.isInteger(userId) || userId <= 0) {
      setProfile(null)
      setItems([])
      setError("无效的用户 ID")
      return
    }

    setLoading(true)
    setError(null)

    try {
      const profilePromise =
        isOwnProfile && user
          ? Promise.resolve(user as PublicUserProfile)
          : authService.getUserById(userId)
      const postsPromise =
        isOwnProfile && tokens?.accessToken
          ? knowpostService.mine(1, PAGE_SIZE, tokens.accessToken)
          : knowpostService.user(userId, 1, PAGE_SIZE, tokens?.accessToken)
      const [profileResp, postsResp] = await Promise.all([profilePromise, postsPromise])

      setProfile(profileResp)
      setItems(postsResp.items ?? [])
    } catch (err) {
      setProfile(null)
      setItems([])
      setError(err instanceof Error ? err.message : "加载失败")
    } finally {
      setLoading(false)
    }
  }, [authLoading, isOwnProfile, tokens?.accessToken, user, userId])

  useEffect(() => {
    if (authLoading) return
    void reload()
  }, [authLoading, reload])

  const pageTitle = isOwnProfile ? "我的主页" : "用户主页"
  const pageSubtitle = isOwnProfile
    ? "这是你公开主页的展示效果"
    : "查看对方公开资料与已发布知文"

  const headerAction = useMemo(() => {
    if (!Number.isInteger(userId) || userId <= 0) return null
    if (isOwnProfile) {
      return (
        <Link href="/app/profile">
          <Button variant="outline" size="sm">
            返回我的主页
          </Button>
        </Link>
      )
    }
    return <FollowButton targetUserId={userId} />
  }, [isOwnProfile, userId])

  return (
    <UserProfilePanel
      pageTitle={pageTitle}
      pageSubtitle={pageSubtitle}
      profile={profile}
      headerAction={headerAction}
      postsTitle={isOwnProfile ? "我的知文" : "Ta 的知文"}
      items={items}
      loading={authLoading || loading}
      error={error}
      emptyText={isOwnProfile ? "暂无内容" : "暂无公开知文"}
    />
  )
}
