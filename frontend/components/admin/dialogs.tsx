"use client"

/**
 * 后台通用弹窗组件。
 *
 * - ConfirmDialog：替代 window.confirm，用于"确认/取消"类危险或常规操作。
 * - OptionDialog：单选弹窗，用于在固定选项中选择（替代让用户手输的 window.prompt）。
 *
 * 两者共享同一视觉外壳（毛玻璃遮罩 + 卡片 + 入场动画），保证后台弹窗风格统一。
 */

import { useEffect, useState, type ReactNode } from "react"
import { Button } from "@/components/ui/button"
import { Loader2, AlertTriangle } from "lucide-react"
import { cn } from "@/lib/utils"

/* ---------------- 共享外壳 ---------------- */

function DialogShell({
  open,
  onClose,
  icon,
  title,
  description,
  children,
  size = "md",
}: {
  open: boolean
  onClose: () => void
  icon?: ReactNode
  title: ReactNode
  description?: ReactNode
  children?: ReactNode
  size?: "sm" | "md"
}) {
  // ESC 关闭
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose()
    }
    document.addEventListener("keydown", onKey)
    return () => document.removeEventListener("keydown", onKey)
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4 backdrop-blur-sm animate-in fade-in-0 duration-150"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        className={cn(
          "w-full overflow-hidden rounded-2xl bg-white p-6 shadow-2xl ring-1 ring-black/5 animate-in zoom-in-95 fade-in-0 duration-150",
          size === "md" ? "max-w-md" : "max-w-sm",
        )}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start gap-3">
          {icon}
          <div className="min-w-0 flex-1">
            <div className="text-base font-semibold text-slate-900">{title}</div>
            {description && (
              <div className="mt-1 text-sm leading-relaxed text-slate-500">{description}</div>
            )}
          </div>
        </div>
        {children && <div className="mt-5">{children}</div>}
      </div>
    </div>
  )
}

/* ---------------- 确认弹窗 ---------------- */

export type ConfirmState = {
  title: string
  description?: string
  /** 危险操作：确认按钮变红并显示警告图标 */
  danger?: boolean
  confirmText?: string
  /** 异步操作；无论成功失败，结束后都会关闭弹窗（错误由调用方自行展示） */
  onConfirm: () => void | Promise<void>
}

export function ConfirmDialog({
  state,
  onClose,
}: {
  state: ConfirmState | null
  onClose: () => void
}) {
  const [loading, setLoading] = useState(false)

  const handleConfirm = async () => {
    if (!state) return
    setLoading(true)
    try {
      await state.onConfirm()
    } finally {
      setLoading(false)
      onClose()
    }
  }

  // 加载中禁止关闭，避免重复触发
  const guardedClose = loading ? () => {} : onClose

  return (
    <DialogShell
      open={!!state}
      onClose={guardedClose}
      icon={
        state?.danger ? (
          <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-md bg-red-100 text-red-600">
            <AlertTriangle className="size-5" />
          </span>
        ) : undefined
      }
      title={state?.title ?? ""}
      description={state?.description}
    >
      <div className="flex justify-end gap-2">
        <Button variant="outline" className="h-9" disabled={loading} onClick={onClose}>
          取消
        </Button>
        <Button
          className={cn(
            "h-9",
            state?.danger && "bg-red-600 text-white hover:bg-red-700",
          )}
          disabled={loading}
          onClick={handleConfirm}
        >
          {loading && <Loader2 className="size-4 animate-spin" />}
          {state?.confirmText ?? "确认"}
        </Button>
      </div>
    </DialogShell>
  )
}

/* ---------------- 单选弹窗 ---------------- */

export type OptionItem = { value: string; label: string; desc?: string }

export function OptionDialog({
  open,
  title,
  description,
  options,
  value,
  onChange,
  onConfirm,
  onClose,
  confirmText = "确认",
  confirmDisabled = false,
}: {
  open: boolean
  title: ReactNode
  description?: ReactNode
  options: OptionItem[]
  value: string
  onChange: (v: string) => void
  onConfirm: () => void | Promise<void>
  onClose: () => void
  confirmText?: string
  confirmDisabled?: boolean
}) {
  const [loading, setLoading] = useState(false)
  const guardedClose = loading ? () => {} : onClose

  const handleConfirm = async () => {
    setLoading(true)
    try {
      await onConfirm()
    } finally {
      setLoading(false)
      onClose()
    }
  }

  return (
    <DialogShell open={open} onClose={guardedClose} title={title} description={description}>
      <div className="space-y-2">
        {options.map((opt) => {
          const active = value === opt.value
          return (
            <label
              key={opt.value}
              className={cn(
                "flex cursor-pointer items-center gap-3 rounded-xl border p-3 transition-colors",
                active
                  ? "border-primary bg-accent ring-1 ring-primary/30"
                  : "border-slate-200 hover:border-slate-300 hover:bg-slate-50",
              )}
            >
              <input
                type="radio"
                name="option"
                checked={active}
                onChange={() => onChange(opt.value)}
                className="size-4 shrink-0 accent-primary"
              />
              <div className="min-w-0">
                <div className="text-sm font-medium text-slate-800">{opt.label}</div>
                {opt.desc && <div className="mt-0.5 text-xs text-slate-500">{opt.desc}</div>}
              </div>
            </label>
          )
        })}
      </div>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="outline" className="h-9" disabled={loading} onClick={onClose}>
          取消
        </Button>
        <Button
          className="h-9"
          disabled={loading || confirmDisabled}
          onClick={handleConfirm}
        >
          {loading && <Loader2 className="size-4 animate-spin" />}
          {confirmText}
        </Button>
      </div>
    </DialogShell>
  )
}
