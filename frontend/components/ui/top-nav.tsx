"use client"

import {
  BookOpenText,
  LogOut,
  MessageSquareText,
  PenLine,
  Search,
  Trophy,
  User,
} from "lucide-react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"

import { useAuth } from "@/components/auth/auth-context"
import { LineLogo } from "@/components/brand/line-logo"
import { UserAvatar } from "@/components/ui/user-avatar"
import { cn } from "@/lib/utils"

const navItems = [
  { href: "/app", label: "阅读", icon: BookOpenText },
  { href: "/app/search", label: "搜索", icon: Search },
  { href: "/app/leaderboard", label: "榜单", icon: Trophy },
  { href: "/app/profile", label: "我的", icon: User },
  { href: "/app/qa", label: "AI 问答", icon: MessageSquareText },
] as const

export function TopNav() {
  const { user, logout, isLoading } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const displayName = user?.nickname || user?.lineId || user?.email || "未登录用户"

  return (
    <header className="fixed inset-x-0 top-0 z-40 border-b border-[#deded8]/90 bg-[#f7f7f3]/92 backdrop-blur-xl">
      <nav
        aria-label="主导航"
        className="mx-auto flex h-[4.5rem] w-full max-w-[1280px] items-center gap-5 px-4 sm:px-6 lg:px-8"
      >
        <Link
          href="/app"
          aria-label="Line 首页"
          className="group flex shrink-0 items-center gap-2.5 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50] focus-visible:ring-offset-4 focus-visible:ring-offset-[#f7f7f3]"
        >
          <LineLogo
            variant="mark"
            className="size-8 shrink-0 transition-transform duration-300 group-hover:-rotate-3"
            priority
          />
          <span className="font-display text-lg font-semibold tracking-[-0.04em] text-[#1d211f]">
            Line
          </span>
        </Link>

        <div className="hidden h-full flex-1 items-center justify-center gap-7 md:flex">
          {navItems.map(({ href, label }) => {
            const isActive =
              href === "/app" ? pathname === "/app" : pathname.startsWith(href)
            return (
              <Link
                key={href}
                href={href}
                aria-current={isActive ? "page" : undefined}
                className={cn(
                  "relative flex h-full items-center text-[13px] font-medium tracking-[0.01em] text-[#696d69] transition-colors duration-200 after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:origin-left after:scale-x-0 after:bg-[#2f5d50] after:transition-transform after:duration-200 hover:text-[#1d211f] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]",
                  isActive && "text-[#1d211f] after:scale-x-100",
                )}
              >
                {label}
              </Link>
            )
          })}
        </div>

        <div className="ml-auto flex shrink-0 items-center gap-2 sm:gap-3">
          {user && (user.role === "ADMIN" || user.role === "SUPER_ADMIN") && (
            <Link
              href="/admin"
              className="hidden rounded-lg px-2 py-2 text-xs font-medium text-[#696d69] transition-colors hover:bg-[#ecece6] hover:text-[#1d211f] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50] sm:inline-flex"
            >
              管理后台
            </Link>
          )}

          <Link
            href="/app/posts/create"
            className="inline-flex h-9 items-center gap-2 rounded-lg bg-[#1d211f] px-3.5 text-xs font-semibold text-white shadow-[0_8px_20px_-12px_rgba(29,33,31,0.8)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-[#2f5d50] active:translate-y-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50] focus-visible:ring-offset-2 focus-visible:ring-offset-[#f7f7f3]"
          >
            <PenLine className="size-3.5" />
            <span className="hidden sm:inline">开始创作</span>
            <span className="sm:hidden">创作</span>
          </Link>

          <Link
            href={
              isLoading
                ? "/app/profile"
                : user
                  ? "/app/profile/edit"
                  : `/login?next=${encodeURIComponent(pathname)}`
            }
            aria-label={isLoading ? "我的" : displayName}
            title={isLoading ? "我的" : displayName}
            className="rounded-lg p-0.5 transition-colors hover:bg-[#ecece6] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
          >
            <UserAvatar
              src={isLoading ? undefined : user?.avatar || undefined}
              nickname={isLoading ? undefined : displayName}
              size="sm"
              className="size-8 rounded-lg ring-1 ring-[#d7d8d2]"
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
              className="hidden size-9 items-center justify-center rounded-lg text-[#696d69] transition-colors hover:bg-[#ecece6] hover:text-[#1d211f] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50] lg:flex"
            >
              <LogOut className="size-4" />
            </button>
          )}
        </div>
      </nav>

      <div className="no-scrollbar mx-auto flex h-11 w-full max-w-[1280px] items-stretch gap-1 overflow-x-auto border-t border-[#e7e7e1] px-3 md:hidden">
        {navItems.map(({ href, label, icon: Icon }) => {
          const isActive =
            href === "/app" ? pathname === "/app" : pathname.startsWith(href)
          return (
            <Link
              key={href}
              href={href}
              aria-current={isActive ? "page" : undefined}
              className={cn(
                "flex shrink-0 items-center gap-1.5 border-b-2 border-transparent px-3 text-xs font-medium text-[#747873] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#2f5d50]",
                isActive && "border-[#2f5d50] text-[#1d211f]",
              )}
            >
              <Icon className="size-3.5" />
              {label}
            </Link>
          )
        })}
      </div>
    </header>
  )
}
