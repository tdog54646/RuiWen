"use client"

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { cn } from "@/lib/utils"

type UserAvatarProps = {
  src?: string | null
  nickname?: string
  className?: string
  size?: "default" | "sm" | "lg"
}

function getInitials(name?: string) {
  if (!name) return "?"
  return name.slice(0, 1).toUpperCase()
}

export function UserAvatar({ src, nickname, className, size = "default" }: UserAvatarProps) {
  return (
    <Avatar size={size} className={cn(className)}>
      {src && <AvatarImage src={src} alt={nickname || "用户头像"} />}
      <AvatarFallback>{getInitials(nickname)}</AvatarFallback>
    </Avatar>
  )
}
