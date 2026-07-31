"use client"

import type { ElementType, ReactNode } from "react"
import { AnimatePresence, motion } from "framer-motion"
import { AlertCircle, CheckCircle2, Loader2 } from "lucide-react"
import Link from "next/link"
import { LineLogo } from "@/components/brand/line-logo"
import { SiteFooter } from "@/components/layout/site-footer"
import { cn } from "@/lib/utils"

const SPRING_EASE = [0.22, 1, 0.36, 1] as const

/* 极光 + 网格背景层 */
export function AuroraBackground({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        "pointer-events-none absolute inset-0 overflow-hidden",
        className,
      )}
      aria-hidden
    >
      <div className="studio-aurora" />
      <div className="studio-grid" />
    </div>
  )
}

/* 页面外壳：背景 FX + 内容容器 */
export function StudioShell({
  children,
  className,
  centered,
}: {
  children: ReactNode
  className?: string
  centered?: boolean
}) {
  return (
    <div className={cn("relative min-h-[70vh]", className)}>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.4 }}
        className={cn(
          "relative flex flex-col gap-5",
          centered && "items-center justify-center",
        )}
      >
        {children}
      </motion.div>
    </div>
  )
}

/* 页面页眉：无气泡、无渐变的编辑部式信息层级。 */
export function PageHeader({
  title,
  subtitle,
  badge,
  chips,
  className,
}: {
  title: ReactNode
  subtitle?: ReactNode
  badge?: ReactNode
  chips?: ReactNode
  className?: string
}) {
  return (
    <motion.header
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, ease: SPRING_EASE }}
      className={cn(
        "flex flex-col gap-5 pb-5 pt-4 md:flex-row md:items-end md:justify-between md:pb-7 md:pt-7",
        className,
      )}
    >
      <div className="flex flex-col gap-3">
        {badge && <div className="flex flex-wrap items-center gap-3">{badge}</div>}
        <div>
          <h1 className="font-display text-balance text-4xl font-medium leading-[1.05] tracking-[-0.055em] text-[#1d211f] md:text-5xl">
            {title}
          </h1>
          {subtitle && (
            <p className="mt-3 max-w-2xl text-pretty text-[15px] leading-6 text-[#70746f]">
              {subtitle}
            </p>
          )}
        </div>
      </div>
      {chips && <div className="flex flex-wrap items-center gap-3">{chips}</div>}
    </motion.header>
  )
}

/* 玻璃卡片：渐变描边 + 入场/悬停动效 */
export function GlassCard({
  children,
  className,
  contentClassName,
  delay = 0,
  disableHover = false,
}: {
  children: ReactNode
  className?: string
  contentClassName?: string
  delay?: number
  disableHover?: boolean
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20, scale: 0.985 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ delay, duration: 0.55, ease: SPRING_EASE }}
      whileHover={disableHover ? undefined : { y: -2 }}
      className={cn(
        "relative rounded-xl border border-[#deded8] bg-[#fbfbf8] p-5 shadow-[0_18px_50px_-42px_rgba(37,54,46,0.5)]",
        className,
      )}
    >
      <div className={cn("relative z-[2]", contentClassName)}>{children}</div>
    </motion.div>
  )
}

/* 区段小标题 */
export function SectionLabel({
  children,
  className,
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <span
      className={cn(
        "text-sm font-semibold text-[#525a55]",
        className,
      )}
    >
      {children}
    </span>
  )
}

/* 无容器状态文字：替代页面中的胶囊气泡。 */
export function StatusChip({
  icon: Icon,
  children,
  tone = "default",
}: {
  icon?: ElementType
  children: ReactNode
  tone?: "default" | "amber" | "emerald"
}) {
  const toneClass = {
    default: "text-[#6f746f]",
    amber: "text-[#806b3d]",
    emerald: "text-[#2f5d50]",
  }[tone]
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 text-xs font-semibold",
        toneClass,
      )}
    >
      {Icon && <Icon className="size-3.5" strokeWidth={1.7} />}
      {children}
    </span>
  )
}

/* 开关 */
export function Toggle({
  checked,
  onChange,
  loading = false,
  interactive = true,
}: {
  checked: boolean
  onChange: () => void
  loading?: boolean
  interactive?: boolean
}) {
  const Tag: ElementType = interactive ? "button" : "div"
  return (
    <Tag
      type={interactive ? "button" : undefined}
      role="switch"
      aria-checked={checked}
      onClick={interactive ? onChange : undefined}
      className={cn(
        "relative h-6 w-11 shrink-0 rounded-full transition-colors duration-300",
        checked
          ? "bg-[#2f5d50]"
          : "bg-[#cfd2cc]",
        loading && "opacity-60",
        !interactive && "pointer-events-none",
      )}
    >
      <motion.span
        animate={{ x: checked ? 22 : 2 }}
        transition={{ type: "spring", stiffness: 500, damping: 32 }}
        className="absolute left-0 top-0.5 size-5 rounded-full bg-white shadow-md ring-1 ring-black/5"
      />
    </Tag>
  )
}

/* 错误/成功消息条（自带 AnimatePresence） */
export function MessageBanner({
  tone,
  show,
  children,
}: {
  tone: "error" | "success"
  show: boolean
  children: ReactNode
}) {
  return (
    <AnimatePresence>
      {show && (
        <motion.div
          initial={{ opacity: 0, y: -8, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: -8, scale: 0.98 }}
          transition={{ type: "spring", stiffness: 400, damping: 30 }}
          className={cn(
            "flex items-center gap-2 border-l-2 px-4 py-3 text-sm",
            tone === "error"
              ? "border-red-500 bg-red-50/60 text-red-700"
              : "border-[#2f5d50] bg-[#e7eee9] text-[#2f5d50]",
          )}
        >
          {tone === "error" ? (
            <AlertCircle className="size-4 shrink-0" />
          ) : (
            <CheckCircle2 className="size-4 shrink-0" />
          )}
          {children}
        </motion.div>
      )}
    </AnimatePresence>
  )
}

/* 空态 / 加载态 */
export function EmptyState({
  children,
  loading,
  className,
}: {
  children?: ReactNode
  loading?: boolean
  className?: string
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center gap-2 py-12 text-center text-sm text-slate-400",
        className,
      )}
    >
      {loading ? (
        <>
          <Loader2 className="size-5 animate-spin text-[#2f5d50]" />
          <span>加载中…</span>
        </>
      ) : (
        children
      )}
    </div>
  )
}

/* 玻璃风输入框样式（配合 shadcn Input/textarea 使用） */
export const glassInputClass =
  "h-11 rounded-lg border-[#d8d9d2] bg-[#fbfbf8] transition-colors placeholder:text-[#969994] focus-visible:border-[#2f5d50] focus-visible:ring-[#2f5d50]/15"

/* 全屏玻璃壳（用于登录/注册/重置等独立页） */
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="app-canvas relative min-h-dvh overflow-x-hidden">
      <div className="relative mx-auto grid min-h-dvh w-full max-w-[1280px] lg:grid-cols-[minmax(0,1.12fr)_minmax(28rem,0.88fr)]">
        <aside className="hidden flex-col justify-between border-r border-[#d8d9d2] px-12 py-12 lg:flex xl:px-16 xl:py-14">
          <Link
            href="/app"
            className="flex w-fit items-center gap-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
          >
            <LineLogo variant="mark" className="size-9" priority />
            <span className="font-display text-xl font-semibold tracking-[-0.04em] text-[#1d211f]">
              Line
            </span>
          </Link>

          <div className="max-w-xl pb-8">
            <p className="font-display text-balance text-5xl font-medium leading-[1.08] tracking-[-0.055em] text-[#202522] xl:text-6xl">
              认真写下的内容，值得被再次找到。
            </p>
            <p className="mt-7 max-w-md text-sm leading-7 text-[#717570]">
              登录 Line，继续阅读、记录和整理你的知识。
            </p>
          </div>

          <SiteFooter align="start" />
        </aside>

        <div className="flex min-h-dvh items-center justify-center px-5 py-10 sm:px-10 lg:px-14">
          <motion.main
            initial={{ opacity: 0, y: 18 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.55, ease: SPRING_EASE }}
            className="w-full max-w-md"
          >
            <Link
              href="/app"
              className="mb-12 flex w-fit items-center gap-2.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50] lg:hidden"
            >
              <LineLogo variant="mark" className="size-8" priority />
              <span className="font-display text-lg font-semibold">Line</span>
            </Link>
            {children}
            <SiteFooter className="mt-10 lg:hidden" />
          </motion.main>
        </div>
      </div>
    </div>
  )
}
