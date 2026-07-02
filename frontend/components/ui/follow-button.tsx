"use client"

import { useEffect, useState } from "react"
import { Check, Plus } from "lucide-react"
import { relationService } from "@/lib/api/relation"
import { useAuth } from "@/components/auth/auth-context"
import { Button } from "@/components/ui/button"

type FollowButtonProps = {
  targetUserId?: number
}

export function FollowButton({ targetUserId }: FollowButtonProps) {
  const { tokens, isLoading: authLoading } = useAuth()
  const [loading, setLoading] = useState(false)
  const [following, setFollowing] = useState(false)
  const [mutual, setMutual] = useState(false)

  useEffect(() => {
    if (authLoading) return
    if (!targetUserId || !tokens?.accessToken) return
    relationService
      .status(targetUserId, tokens.accessToken)
      .then((s) => {
        setFollowing(s.following)
        setMutual(s.mutual)
      })
      .catch(() => {})
  }, [authLoading, targetUserId, tokens?.accessToken])

  if (authLoading || !targetUserId || !tokens?.accessToken) return null

  const onClick = async () => {
    if (!tokens?.accessToken) return
    setLoading(true)
    try {
      if (following) {
        await relationService.unfollow(targetUserId, tokens.accessToken)
        setFollowing(false)
        setMutual(false)
      } else {
        await relationService.follow(targetUserId, tokens.accessToken)
        setFollowing(true)
        try {
          const s = await relationService.status(targetUserId, tokens.accessToken)
          setMutual(s.mutual)
        } catch {}
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <Button
      variant={following ? "outline" : "default"}
      size="sm"
      onClick={onClick}
      disabled={loading}
      className="gap-1"
    >
      {following ? <Check className="size-3.5" /> : <Plus className="size-3.5" />}
      {following ? "已关注" : "关注"}
      {mutual && <span className="text-xs text-muted-foreground">互关</span>}
    </Button>
  )
}
