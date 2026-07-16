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
      <div className="flex min-h-dvh items-center justify-center text-sm text-slate-400">
        正在校验管理员身份...
      </div>
    )
  }

  return (
    <div className="flex min-h-dvh bg-slate-50 text-slate-800">
      {/* 侧边栏 */}
      <aside className="sticky top-0 flex h-dvh w-56 shrink-0 flex-col border-r border-slate-200 bg-white">
        <div className="flex items-center gap-2 px-5 py-5">
          <LineLogo variant="mark" className="size-8 shrink-0" priority />
          <div className="leading-tight">
            <p className="text-sm font-semibold">Line</p>
            <p className="text-[11px] text-slate-400">后台管理</p>
          </div>
        </div>
        <nav className="flex flex-1 flex-col gap-1 px-3 py-2">
          {navItems.map(({ href, label, icon: Icon }) => {
            const isActive = href === "/admin" ? pathname === "/admin" : pathname.startsWith(href)
            return (
              <Link
                key={href}
                href={href}
                className={cn(
                  "flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-slate-900 text-white"
                    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900",
                )}
              >
                <Icon className="size-4" />
                {label}
              </Link>
            )
          })}
        </nav>
        <div className="flex flex-col gap-1 border-t border-slate-200 px-3 py-3">
          <Link
            href="/app"
            className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-600 hover:bg-slate-100 hover:text-slate-900"
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
            className="flex items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-slate-600 hover:bg-slate-100 hover:text-slate-900"
          >
            <LogOut className="size-4" />
            退出登录
          </button>
        </div>
      </aside>

      {/* 主内容 */}
      <main className="flex-1 overflow-x-hidden">
        <div className="mx-auto w-full max-w-6xl px-6 py-8">{children}</div>
      </main>
    </div>
  )
}
