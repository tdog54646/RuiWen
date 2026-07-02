"use client"

import { Home, LogOut, MessageSquare, PenSquare, Search, Trophy, User } from "lucide-react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"

import { useAuth } from "@/components/auth/auth-context"
import { Sidebar } from "@/components/ui/sidebar"
import { UserAvatar } from "@/components/ui/user-avatar"
import { cn } from "@/lib/utils"

const navItems = [
  { href: "/app", label: "首页", icon: Home },
  { href: "/app/search", label: "搜索", icon: Search },
  { href: "/app/posts/create", label: "创作", icon: PenSquare },
  { href: "/app/leaderboard", label: "排行榜", icon: Trophy },
  { href: "/app/profile", label: "我的", icon: User },
  { href: "/app/qa", label: "AI问答", icon: MessageSquare },
] as const

export function SidebarDemo() {
  const { user, logout } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const displayName = user?.nickname || user?.lineId || user?.email || "未登录用户"

  return (
    <Sidebar>
      {({ expanded, closeMobile }) => (
        <div className="flex h-full w-full flex-col p-3">
          <Link
            href="/app"
            className={cn(
              "mb-2 flex h-12 items-center rounded-xl border border-sidebar-border bg-sidebar-muted px-3",
              expanded ? "justify-start gap-3" : "justify-center",
            )}
            onClick={closeMobile}
          >
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-black text-[9px] font-black leading-none tracking-tight text-white">
              LINE
            </span>
            <span
              className={cn(
                "text-sm font-semibold transition-opacity",
                expanded ? "opacity-100" : "pointer-events-none w-0 opacity-0",
              )}
            >
              Line
            </span>
          </Link>

          <nav className="flex flex-col gap-1">
            {navItems.map(({ href, label, icon: Icon }) => {
              const isActive =
                href === "/app" ? pathname === "/app" : pathname.startsWith(href)

              return (
                <Link
                  key={href}
                  href={href}
                  onClick={closeMobile}
                  className={cn(
                    "flex h-11 items-center rounded-xl px-3 text-sm font-medium transition-colors",
                    expanded ? "justify-start gap-3" : "justify-center",
                    isActive
                      ? "bg-sidebar-primary text-sidebar-primary-foreground shadow-sm"
                      : "text-sidebar-foreground/85 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
                  )}
                >
                  <Icon className="size-4 shrink-0" />
                  <span
                    className={cn(
                      "whitespace-nowrap transition-opacity",
                      expanded ? "opacity-100" : "pointer-events-none w-0 opacity-0",
                    )}
                  >
                    {label}
                  </span>
                </Link>
              )
            })}
            {user && (
              <button
                type="button"
                onClick={async () => {
                  closeMobile()
                  await logout()
                  router.push("/login")
                }}
                className={cn(
                  "flex h-11 cursor-pointer items-center rounded-xl px-3 text-sm font-medium transition-colors text-sidebar-foreground/85 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
                  expanded ? "justify-start gap-3" : "justify-center",
                )}
              >
                <LogOut className="size-4 shrink-0" />
                <span
                  className={cn(
                    "whitespace-nowrap transition-opacity",
                    expanded ? "opacity-100" : "pointer-events-none w-0 opacity-0",
                  )}
                >
                  退出
                </span>
              </button>
            )}
          </nav>

          <div className="mt-auto overflow-hidden rounded-xl border border-sidebar-border bg-sidebar-muted">
            <Link
              href={user ? "/app/profile/edit" : `/login?next=${encodeURIComponent(pathname)}`}
              onClick={closeMobile}
              className={cn("flex items-center p-3 transition-colors hover:bg-sidebar-accent/70", expanded ? "gap-3" : "justify-center")}
            >
              <UserAvatar
                src={user?.avatar || undefined}
                nickname={displayName}
                className="size-9 border border-sidebar-border"
              />
              <div
                className={cn(
                  "min-w-0 transition-opacity",
                  expanded ? "opacity-100" : "pointer-events-none w-0 opacity-0",
                )}
              >
                <p className="truncate text-sm font-medium">{displayName}</p>
              </div>
            </Link>
          </div>
        </div>
      )}
    </Sidebar>
  )
}
