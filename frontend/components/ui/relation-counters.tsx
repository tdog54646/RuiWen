"use client"

import { useEffect, useState } from "react"
import { relationService } from "@/lib/api/relation"
import { useAuth } from "@/components/auth/auth-context"
import type { RelationCountersResponse } from "@/lib/types/relation"
import { RelationListModal } from "./relation-list-modal"

type RelationCountersProps = {
  userId?: number
}

export function RelationCounters({ userId }: RelationCountersProps) {
  const { tokens, isLoading: authLoading } = useAuth()
  const [counts, setCounts] = useState<RelationCountersResponse | null>(null)
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<"following" | "followers">("following")

  useEffect(() => {
    if (authLoading) return
    if (!userId) return
    relationService
      .counters(userId, tokens?.accessToken)
      .then(setCounts)
      .catch(() => {})
  }, [authLoading, userId, tokens?.accessToken])

  if (!userId || !counts) return null

  const items = [
    {
      label: "关注",
      value: counts.followings,
      clickable: true,
      onClick: () => { setMode("following"); setOpen(true) },
    },
    {
      label: "粉丝",
      value: counts.followers,
      clickable: true,
      onClick: () => { setMode("followers"); setOpen(true) },
    },
    { label: "发帖", value: counts.posts },
    { label: "获赞", value: counts.likedPosts },
    { label: "获藏", value: counts.favedPosts },
  ]

  return (
    <>
      <div className="flex items-center gap-5">
        {items.map((item) => (
          <div
            key={item.label}
            className={`flex items-baseline gap-1.5 text-muted-foreground ${item.clickable ? "cursor-pointer" : ""}`}
            onClick={item.onClick}
          >
            <span className="font-bold text-foreground">{item.value}</span>
            <span className="text-xs">{item.label}</span>
          </div>
        ))}
      </div>
      <RelationListModal
        open={open}
        onClose={() => setOpen(false)}
        userId={userId}
        mode={mode}
      />
    </>
  )
}
