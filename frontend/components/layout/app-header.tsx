"use client"

import type { ReactNode } from "react"
import { AuthStatus } from "@/components/ui/auth-status"

export type AppHeaderProps = {
  headline: string
  subtitle?: string
  rightSlot?: ReactNode
  children?: ReactNode
}

export function AppHeader({
  headline,
  subtitle,
  rightSlot,
  children,
}: AppHeaderProps) {
  return (
    <header className="flex flex-col gap-4 rounded-2xl bg-gradient-to-br from-primary/10 via-primary/5 to-background p-6 shadow-sm">
      <div className="flex items-center justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight">{headline}</h1>
          {subtitle && (
            <p className="text-sm text-muted-foreground">{subtitle}</p>
          )}
        </div>
        <div className="flex items-center gap-3">
          {rightSlot}
          <AuthStatus />
        </div>
      </div>
      {children}
    </header>
  )
}
