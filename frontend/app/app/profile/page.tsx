"use client"

import { useCallback, useEffect, useState } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/components/auth/auth-context"
import { UserProfilePanel } from "@/components/ui/user-profile-panel"
import { knowpostService } from "@/lib/api/knowpost"
import type { FeedItem } from "@/lib/types/knowpost"

export default function ProfilePage() {
  const { user, tokens } = useAuth()
  const [items, setItems] = useState<FeedItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reloadMine = useCallback(async () => {
    if (!tokens?.accessToken) return
    setLoading(true)
    setError(null)
    try {
      const resp = await knowpostService.mine(1, 20, tokens.accessToken)
      setItems(resp.items ?? [])
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载失败")
    } finally {
      setLoading(false)
    }
  }, [tokens?.accessToken])

  useEffect(() => {
    void reloadMine()
  }, [reloadMine])

  return (
    <UserProfilePanel
      pageTitle="我的主页"
      pageSubtitle="完善个人信息，积累你的知识资产"
      profile={user}
      headerAction={(
        <Link href="/app/profile/edit">
          <Button variant="outline" size="sm" className="rounded-lg border-[#cfd1ca] bg-transparent hover:bg-[#ecece6]">
            编辑资料
          </Button>
        </Link>
      )}
      postsTitle="我的知文"
      items={items}
      loading={loading}
      error={error}
      emptyText={user ? "暂无内容" : "请登录后查看你的知文"}
      editable
      onPostChanged={(itemId, action) => {
        if (action === "delete") {
          setItems((prev) => prev.filter((item) => item.id !== itemId))
          return
        }
        void reloadMine()
      }}
    />
  )
}
