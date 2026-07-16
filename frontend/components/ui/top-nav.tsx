"use client"

import { Home, LogOut, MessageSquare, PenSquare, Search, Trophy, User } from "lucide-react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { useState } from "react"
import {
  motion,
  useMotionTemplate,
  useMotionValueEvent,
  useScroll,
  useSpring,
  useTransform,
  useVelocity,
} from "framer-motion"

import { useAuth } from "@/components/auth/auth-context"
import { LineLogo } from "@/components/brand/line-logo"
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

/**
 * 液态玻璃 SVG 滤镜（全局只需渲染一次）：
 * feTurbulence 噪声 → feGaussianBlur 柔化成位移图 →
 * feDisplacementMap 把背景内容真实折射扭曲 + feSpecularLighting 高光。
 */
function GlassFilter() {
  return (
    <svg aria-hidden className="pointer-events-none absolute size-0">
      <filter
        id="nav-glass-distortion"
        x="-10%"
        y="-10%"
        width="120%"
        height="120%"
      >
        <feTurbulence
          type="fractalNoise"
          baseFrequency="0.002 0.008"
          numOctaves="2"
          seed="17"
          result="noise"
        />
        <feGaussianBlur in="noise" stdDeviation="2" result="softMap" />
        <feDisplacementMap
          in="SourceGraphic"
          in2="softMap"
          scale="80"
          xChannelSelector="R"
          yChannelSelector="G"
          result="refracted"
        />
        <feSpecularLighting
          in="softMap"
          surfaceScale="6"
          specularConstant="1"
          specularExponent="90"
          lightingColor="white"
          result="spec"
        >
          <fePointLight x="-150" y="-150" z="250" />
        </feSpecularLighting>
        <feComposite in="spec" in2="refracted" operator="in" result="specMasked" />
        <feComposite
          in="refracted"
          in2="specMasked"
          operator="arithmetic"
          k1="0"
          k2="1"
          k3="1"
          k4="0"
        />
      </filter>
    </svg>
  )
}

export function TopNav() {
  const { user, logout, isLoading } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const displayName = user?.nickname || user?.lineId || user?.email || "未登录用户"

  // —— 滚动驱动的液态玻璃 ——
  const { scrollY } = useScroll()
  const [scrolled, setScrolled] = useState(false)
  useMotionValueEvent(scrollY, "change", (latest) => {
    setScrolled(latest > 8)
  })

  // 速度 → 弹性平滑 → 流动光带
  const velocity = useVelocity(scrollY)
  const smooth = useSpring(velocity, {
    damping: 34,
    stiffness: 320,
    mass: 0.5,
  })
  const factor = useTransform(smooth, [-3500, 0, 3500], [-1, 0, 1], {
    clamp: false,
  })
  const absSmooth = useTransform(smooth, (v) => Math.abs(v))

  // 流光光带：静止居中、滚动时随方向横扫并变亮
  const sheenStart = useTransform(factor, [-1, 0, 1], [108, 34, -44])
  const sheenPeak = useTransform(factor, [-1, 0, 1], [128, 50, -28])
  const sheenEnd = useTransform(factor, [-1, 0, 1], [148, 66, -12])
  const sheenOpacity = useTransform(absSmooth, [0, 600, 2600], [0.18, 0.7, 0.95])
  const sheenBg = useMotionTemplate`linear-gradient(105deg, transparent ${sheenStart}%, rgba(255,255,255,0.65) ${sheenPeak}%, transparent ${sheenEnd}%)`

  return (
    <header className="fixed inset-x-0 top-3 z-40 flex justify-center px-3">
      <GlassFilter />
      <nav
        aria-label="主导航"
        className={cn(
          "liquid-glass-nav mx-auto flex w-full max-w-[960px] items-center gap-1 overflow-hidden rounded-full p-1.5",
          scrolled && "is-scrolled",
        )}
      >
        {/* 层 A：磨砂模糊（可靠基底，绝不挂 SVG 滤镜） */}
        <div
          aria-hidden
          className="liquid-blur pointer-events-none absolute inset-0 z-0 rounded-full"
        />
        {/* 层 B：SVG 折射增强（滚动时内容在玻璃后被真实扭曲） */}
        <div
          aria-hidden
          className="liquid-distort pointer-events-none absolute inset-0 z-[1] rounded-full"
        />
        {/* 层 C：环境彩色辉光（纯白底也可见的玻璃反光） */}
        <div
          aria-hidden
          className="liquid-glow pointer-events-none absolute inset-0 z-[12] rounded-full"
        />
        {/* 层 D：玻璃着色 */}
        <div
          aria-hidden
          className="liquid-tint pointer-events-none absolute inset-0 z-10 rounded-full"
        />
        {/* 层 E：流动光带（滚动速度驱动，液态流动感） */}
        <motion.div
          aria-hidden
          className="liquid-sheen pointer-events-none absolute inset-0 z-[18] rounded-full"
          style={{ backgroundImage: sheenBg, opacity: sheenOpacity }}
        />
        {/* 层 F：顶部镜面高光（“从上方打光”的亮线） */}
        <div
          aria-hidden
          className="liquid-rim pointer-events-none absolute inset-0 z-[20] rounded-full"
        />
        {/* 棱镜色散彩边由 .liquid-glass-nav::before 提供 */}

        {/* 品牌 */}
        <Link
          href="/app"
          aria-label="Line 首页"
          className="relative z-30 flex shrink-0 items-center gap-2 rounded-full px-2.5 py-1.5"
        >
          <LineLogo variant="mark" className="size-7 shrink-0" priority />
          <span className="hidden text-sm font-semibold text-slate-800 sm:inline">
            Line
          </span>
        </Link>

        {/* 导航项：移动端仅图标可横滑，桌面端图标+文字居中 */}
        <div className="no-scrollbar relative z-30 -mx-1 flex flex-1 items-center gap-0.5 overflow-x-auto px-1 md:mx-auto md:justify-center md:overflow-visible">
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
        <div className="relative z-30 ml-auto flex shrink-0 items-center gap-0.5 pl-1">
          {user && (user.role === "ADMIN" || user.role === "SUPER_ADMIN") && (
            <Link
              href="/admin"
              title="后台管理"
              aria-label="后台管理"
              className="flex h-9 items-center rounded-full px-3 text-xs font-medium text-slate-600 transition-colors hover:bg-white/60 hover:text-slate-900"
            >
              后台
            </Link>
          )}
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
            className="rounded-full p-0.5 transition-colors hover:bg-white/60"
          >
            <UserAvatar
              src={isLoading ? undefined : user?.avatar || undefined}
              nickname={isLoading ? undefined : displayName}
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
