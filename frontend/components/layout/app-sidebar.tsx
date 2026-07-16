"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { Home, Search, PenSquare, User } from "lucide-react"
import { LineLogo } from "@/components/brand/line-logo"
import { cn } from "@/lib/utils"

const navItems = [
  { href: "/app", label: "首页", icon: Home },
  { href: "/app/search", label: "搜索", icon: Search },
  { href: "/app/posts/create", label: "创作", icon: PenSquare },
  { href: "/app/profile", label: "我的", icon: User },
] as const

export function AppSidebar({ className }: { className?: string }) {
  const pathname = usePathname()

  return (
    <aside
      className={cn(
        "sticky top-6 flex h-fit flex-col items-center gap-6 rounded-2xl bg-background/90 p-4 shadow-lg backdrop-blur-md",
        className,
      )}
    >
      <Link
        href="/app"
        aria-label="Line 首页"
        className="flex size-12 items-center justify-center"
      >
        <LineLogo variant="mark" className="size-12" priority />
      </Link>

      <nav className="flex w-full flex-col gap-2">
        {navItems.map(({ href, label, icon: Icon }) => {
          const isActive =
            href === "/app"
              ? pathname === "/app"
              : pathname.startsWith(href)
          return (
            <Link
              key={href}
              href={href}
              className={cn(
                "flex flex-col items-center gap-1 rounded-xl px-2 py-3 text-xs font-medium text-muted-foreground transition-colors",
                isActive &&
                  "bg-primary/10 text-primary shadow-sm",
              )}
            >
              <Icon className="size-5" />
              {label}
            </Link>
          )
        })}
      </nav>

      <div className="mt-auto flex flex-col items-center gap-1 pt-4 text-center text-[11px] text-muted-foreground">
        <span className="font-semibold text-primary">Line</span>
        <span>让知识发光</span>
      </div>
    </aside>
  )
}
