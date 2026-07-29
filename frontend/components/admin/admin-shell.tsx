"use client"

import { useEffect } from "react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import {
  LayoutDashboard,
  Users,
  Shuffle,
  ScrollText,
  FileText,
  Settings,
  LogOut,
  ArrowLeft,
  MessagesSquare,
  Brain,
  Database,
} from "lucide-react"
import { useAuth } from "@/components/auth/auth-context"
import { LineLogo } from "@/components/brand/line-logo"
import { cn } from "@/lib/utils"

const navItems = [
  { href: "/admin", label: "仪表盘", icon: LayoutDashboard },
  { href: "/admin/users", label: "用户管理", icon: Users },
  { href: "/admin/registration", label: "注册策略", icon: Shuffle },
  { href: "/admin/audit", label: "登录审计", icon: ScrollText },
  { href: "/admin/posts", label: "内容审核", icon: FileText },
  { href: "/admin/index", label: "索引库", icon: Database },
  { href: "/admin/conversations", label: "会话审计", icon: MessagesSquare },
  { href: "/admin/memories", label: "用户记忆", icon: Brain },
  { href: "/admin/settings", label: "系统配置", icon: Settings },
] as const

function isAdminRole(role: string | undefined) {
  return role === "ADMIN" || role === "SUPER_ADMIN"
}

export function AdminShell({ children }: { children: React.ReactNode }) {
  const { user, isLoading, logout } = useAuth()
  const router = useRouter()
  const pathname = usePathname()

  // 鉴权守卫：未登录或非管理员重定向
  useEffect(() => {
    if (isLoading) return
    if (!user || !isAdminRole(user.role)) {
      router.replace(`/login?next=${encodeURIComponent("/admin")}`)
    }
  }, [user, isLoading, router])

  if (isLoading || !user || !isAdminRole(user.role)) {
    return (
      <div className="app-canvas flex min-h-dvh items-center justify-center text-sm text-[#7a7e79]">
        正在校验管理员身份...
      </div>
    )
  }

  return (
    <div className="admin-canvas flex min-h-dvh flex-col bg-[#f3f3ef] text-[#252a27] lg:flex-row">
      {/* 侧边栏 */}
      <aside className="sticky top-0 z-40 flex w-full shrink-0 flex-col bg-[#f8f8f4]/95 backdrop-blur-xl lg:h-dvh lg:w-64 lg:border-r lg:border-[#d8d9d2] lg:backdrop-blur-none">
        <div className="flex h-[4.5rem] items-center gap-2.5 px-5 lg:h-auto lg:py-6">
          <LineLogo variant="mark" className="size-8 shrink-0" priority />
          <p className="font-display text-base font-semibold tracking-[-0.035em]">Line</p>
          <Link
            href="/app"
            className="ml-auto text-xs font-medium text-[#69706b] hover:text-[#2f5d50] lg:hidden"
          >
            返回主站
          </Link>
        </div>
        <nav className="no-scrollbar flex flex-row gap-1 overflow-x-auto px-3 pb-3 lg:flex-1 lg:flex-col lg:overflow-y-auto lg:py-3">
          {navItems.map(({ href, label, icon: Icon }) => {
            const isActive = href === "/admin" ? pathname === "/admin" : pathname.startsWith(href)
            return (
              <Link
                key={href}
                href={href}
                className={cn(
                  "flex shrink-0 items-center gap-2 px-3 py-2.5 text-sm font-medium transition-colors lg:border-l-2 lg:border-transparent",
                  isActive
                    ? "border-[#2f5d50] bg-[#e7eee9] text-[#23483e]"
                    : "text-[#69706b] hover:bg-[#ecece6] hover:text-[#1d211f]",
                )}
              >
                <Icon className="size-4" />
                {label}
              </Link>
            )
          })}
        </nav>
        <div className="hidden flex-col gap-1 px-3 py-4 lg:flex">
          <Link
            href="/app"
            className="flex items-center gap-2 border-l-2 border-transparent px-3 py-2 text-sm text-[#69706b] hover:border-[#2f5d50] hover:bg-[#ecece6] hover:text-[#1d211f]"
          >
            <ArrowLeft className="size-4" />
            返回主站
          </Link>
          <button
            type="button"
            onClick={async () => {
              await logout()
              router.push("/login")
            }}
            className="flex items-center gap-2 border-l-2 border-transparent px-3 py-2 text-left text-sm text-[#69706b] hover:border-[#2f5d50] hover:bg-[#ecece6] hover:text-[#1d211f]"
          >
            <LogOut className="size-4" />
            退出登录
          </button>
        </div>
      </aside>

      {/* 主内容 */}
      <main className="min-w-0 flex-1 overflow-x-hidden">
        <div className="mx-auto w-full max-w-[1440px] px-4 py-7 sm:px-6 lg:px-10 lg:py-10">{children}</div>
      </main>
    </div>
  )
}
