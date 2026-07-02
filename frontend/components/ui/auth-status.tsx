"use client"

import { usePathname, useRouter } from "next/navigation"
import { useAuth } from "@/components/auth/auth-context"
import { UserAvatar } from "@/components/ui/user-avatar"
import { Button } from "@/components/ui/button"

export function AuthStatus() {
  const { user, logout, isLoading } = useAuth()
  const router = useRouter()
  const pathname = usePathname()

  if (isLoading) {
    return (
      <span className="text-xs text-muted-foreground">加载中...</span>
    )
  }

  if (!user) {
    return (
      <Button
        variant="outline"
        size="sm"
        onClick={() =>
          router.push(
            `/login?next=${encodeURIComponent(pathname)}`,
          )
        }
      >
        登录
      </Button>
    )
  }

  const displayName = user.nickname || "用户"

  return (
    <div className="flex items-center gap-2">
      <div className="flex items-center gap-2 rounded-full bg-muted/50 px-3 py-1.5">
        <UserAvatar
          src={user.avatar || undefined}
          nickname={displayName}
          className="size-7"
        />
        <span className="text-sm font-medium">{displayName}</span>
      </div>
      <Button
        variant="ghost"
        size="sm"
        className="text-muted-foreground"
        onClick={async () => {
          await logout()
          router.push("/login")
        }}
      >
        退出
      </Button>
    </div>
  )
}
