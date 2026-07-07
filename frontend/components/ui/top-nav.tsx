"use client"

import { Home, LogOut, MessageSquare, PenSquare, Search, Trophy, User } from "lucide-react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"

import { useAuth } from "@/components/auth/auth-context"
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

export function TopNav() {
  const { user, logout } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const displayName = user?.nickname || user?.lineId || user?.email || "未登录用户"

  return (
    <header className="fixed inset-x-0 top-3 z-40 flex justify-center px-3">
      <nav
        aria-label="主导航"
        className="glass-surface glass-border mx-auto flex w-full max-w-[960px] items-center gap-1 rounded-full p-1.5"
      >
        {/* 品牌 */}
        <Link
          href="/app"
          aria-label="Line 首页"
          className="flex shrink-0 items-center gap-2 rounded-full px-2.5 py-1.5"
        >
          <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-black text-[8px] font-black leading-none tracking-tight text-white">
            LINE
          </span>
          <span className="hidden text-sm font-semibold text-slate-800 sm:inline">
            Line
          </span>
        </Link>

        {/* 导航项：移动端仅图标可横滑，桌面端图标+文字居中 */}
        <div className="no-scrollbar -mx-1 flex flex-1 items-center gap-0.5 overflow-x-auto px-1 md:mx-auto md:justify-center md:overflow-visible">
          {navItems.map(({ href, label, icon: Icon }) => {
            const isActive =
              href === "/app" ? pathname === "/app" : pathname.startsWith(href)
            return (
              <Link
                key={href}
                href={href}
                title={label}
                aria-label={label}
                aria-current={isActive ? "page" : undefined}
                className={cn(
                  "flex h-9 shrink-0 items-center gap-1.5 rounded-full px-2.5 text-xs font-medium transition-colors md:text-[13px]",
                  isActive
                    ? "bg-gradient-to-r from-cyan-400/90 to-violet-500/90 text-white shadow-[0_4px_14px_-4px_rgba(139,92,246,0.55)]"
                    : "text-slate-600 hover:bg-white/60 hover:text-slate-900",
                )}
              >
                <Icon className="size-4 shrink-0" />
                <span className="whitespace-nowrap md:inline">{label}</span>
              </Link>
            )
          })}
        </div>

        {/* 右侧：头像 + 登出 */}
        <div className="ml-auto flex shrink-0 items-center gap-0.5 pl-1">
          <Link
            href={user ? "/app/profile/edit" : `/login?next=${encodeURIComponent(pathname)}`}
            aria-label={displayName}
            title={displayName}
            className="rounded-full p-0.5 transition-colors hover:bg-white/60"
          >
            <UserAvatar
              src={user?.avatar || undefined}
              nickname={displayName}
              size="sm"
              className="size-8 ring-1 ring-white/70"
            />
          </Link>
          {user && (
            <button
              type="button"
              onClick={async () => {
                await logout()
                router.push("/login")
              }}
              aria-label="退出登录"
              title="退出登录"
              className="flex size-8 items-center justify-center rounded-full text-slate-600 transition-colors hover:bg-white/60 hover:text-slate-900"
            >
              <LogOut className="size-4" />
            </button>
          )}
        </div>
      </nav>
    </header>
  )
}
