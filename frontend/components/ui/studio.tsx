"use client"

import type { ElementType, ReactNode } from "react"
import { AnimatePresence, motion } from "framer-motion"
import { AlertCircle, CheckCircle2, Loader2 } from "lucide-react"
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

/* 页头：渐变标题 + 副标题 + 徽标 + 状态芯片 */
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
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, ease: SPRING_EASE }}
      className={cn(
        "flex flex-col gap-4 md:flex-row md:items-end md:justify-between",
        className,
      )}
    >
      <div className="flex flex-col gap-3">
        {badge && <div className="flex flex-wrap items-center gap-2">{badge}</div>}
        <div>
          <h1 className="text-gradient text-3xl font-bold tracking-tight md:text-4xl">
            {title}
          </h1>
          {subtitle && <p className="mt-1.5 text-sm text-slate-500">{subtitle}</p>}
        </div>
      </div>
      {chips && <div className="flex flex-wrap items-center gap-2">{chips}</div>}
    </motion.div>
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
      whileHover={disableHover ? undefined : { y: -3 }}
      className={cn(
        "glass-surface glass-border relative rounded-2xl p-5",
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
        "text-[11px] font-medium uppercase tracking-wider text-slate-400",
        className,
      )}
    >
      {children}
    </span>
  )
}

/* 状态胶囊 */
export function StatusChip({
  icon: Icon,
  children,
  tone = "default",
}: {
  icon?: ElementType
  children: ReactNode
  tone?: "default" | "violet" | "cyan" | "amber" | "emerald"
}) {
  const toneClass = {
    default: "text-slate-600",
    violet: "border-violet-200/60 bg-violet-50/70 text-violet-700",
    cyan: "border-cyan-200/60 bg-cyan-50/70 text-cyan-700",
    amber: "border-amber-200/60 bg-amber-50/70 text-amber-700",
    emerald: "border-emerald-200/60 bg-emerald-50/70 text-emerald-700",
  }[tone]
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border border-white/60 bg-white/60 px-3 py-1 text-xs font-medium backdrop-blur-md",
        toneClass,
      )}
    >
      {Icon && <Icon className="size-3.5" />}
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
          ? "bg-gradient-to-r from-cyan-400 to-violet-500 shadow-[0_0_12px_-2px_rgba(139,92,246,0.6)]"
          : "bg-slate-300/80",
        loading && "studio-shimmer",
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
            "flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm backdrop-blur-md",
            tone === "error"
              ? "border border-red-200/60 bg-red-50/70 text-red-600"
              : "border border-emerald-200/60 bg-emerald-50/70 text-emerald-600",
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
          <Loader2 className="size-5 animate-spin text-violet-400" />
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
  "h-11 border-white/60 bg-white/50 backdrop-blur-md transition-colors placeholder:text-slate-400 focus-visible:border-cyan-400/60 focus-visible:bg-white/70 focus-visible:ring-cyan-400/20"

/* 全屏玻璃壳（用于登录/注册/重置等独立页） */
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="relative flex min-h-dvh items-center justify-center overflow-hidden px-4 py-10">
      <AuroraBackground />
      <motion.div
        initial={{ opacity: 0, y: 24, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.6, ease: SPRING_EASE }}
        className="glass-surface glass-border relative z-10 w-full max-w-md rounded-3xl p-8 md:p-10"
      >
        {children}
      </motion.div>
    </div>
  )
}
