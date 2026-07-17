"use client"

import { useRef, useState, useEffect } from "react"
import type { ReactNode } from "react"
import Link from "next/link"
import { MoreHorizontal, Pin, Globe, Lock, Trash2, Pencil } from "lucide-react"
import { motion } from "framer-motion"
import { useAuth } from "@/components/auth/auth-context"
import { knowpostService } from "@/lib/api/knowpost"
import { toast } from "sonner"
import type { VisibleScope } from "@/lib/types/knowpost"
import { UserAvatar } from "./user-avatar"

/** 由标题确定性地生成一个色相，保证同一篇文章占位封面颜色稳定。 */
function hueFrom(seed: string): number {
  let h = 0
  for (let i = 0; i < seed.length; i++) {
    h = (h * 31 + seed.charCodeAt(i)) % 360
  }
  return h
}

/** 无封面图时的渐变占位封面，保证有图/无图卡片高度完全一致。 */
function CoverPlaceholder({ title, tag }: { title: string; tag?: string }) {
  const hue = hueFrom(title)
  const initial = title.trim().charAt(0) || "知"
  return (
    <div
      className="relative flex aspect-[4/3] w-full items-center justify-center overflow-hidden"
      style={{
        background: `linear-gradient(135deg, hsl(${hue} 72% 62%), hsl(${(hue + 48) % 360} 68% 46%))`,
      }}
    >
      <div
        className="absolute inset-0 opacity-25"
        style={{
          backgroundImage:
            "radial-gradient(circle at 22% 20%, rgba(255,255,255,0.9) 0, transparent 42%)",
        }}
      />
      <span className="relative select-none text-6xl font-black text-white/95 drop-shadow-sm">
        {initial}
      </span>
      {tag && (
        <span className="absolute bottom-2 left-2 rounded-full bg-black/20 px-2 py-0.5 text-[11px] font-medium text-white/90 backdrop-blur-sm">
          #{tag}
        </span>
      )}
    </div>
  )
}

export type PostCardProps = {
  id: string
  title: string
  summary: string
  tags: string[]
  authorTags?: string[]
  isTop?: boolean
  visible?: VisibleScope
  teacher: {
    name: string
    avatarUrl?: string
  }
  coverImage?: string
  to?: string
  footerExtra?: ReactNode
  editable?: boolean
  onChanged?: (
    action: "top" | "visibility" | "delete",
    payload?: unknown,
  ) => void
  className?: string
}

export function PostCard({
  id,
  title,
  summary,
  tags,
  authorTags,
  isTop,
  visible,
  teacher,
  coverImage,
  to,
  footerExtra,
  editable = false,
  onChanged,
  className,
}: PostCardProps) {
  const { tokens } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuLoading, setMenuLoading] = useState(false)
  const [menuError, setMenuError] = useState<string | null>(null)
  const [localIsTop, setLocalIsTop] = useState(isTop)
  const [localVisible, setLocalVisible] = useState(visible)
  const btnRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!menuOpen) return
    const handler = (e: MouseEvent) => {
      const target = e.target as Node
      if (menuRef.current?.contains(target) || btnRef.current?.contains(target)) return
      setMenuOpen(false)
    }
    document.addEventListener("mousedown", handler, true)
    return () => document.removeEventListener("mousedown", handler, true)
  }, [menuOpen])

  const handleSetTop = async (val: boolean) => {
    if (!tokens?.accessToken) return
    setMenuLoading(true)
    try {
      await knowpostService.setTop(id, val, tokens.accessToken)
      setLocalIsTop(val)
      setMenuOpen(false)
      onChanged?.("top", { isTop: val })
    } catch (e) {
      setMenuError(e instanceof Error ? e.message : "操作失败")
    } finally {
      setMenuLoading(false)
    }
  }

  const handleSetVisibility = async (next: VisibleScope) => {
    if (!tokens?.accessToken) return
    setMenuLoading(true)
    try {
      await knowpostService.setVisibility(id, next, tokens.accessToken)
      setLocalVisible(next)
      setMenuOpen(false)
      onChanged?.("visibility", { visible: next })
      toast.success(`已设为${next === "public" ? "公开" : "私密"}`)
    } catch (e) {
      const msg = e instanceof Error ? e.message : "操作失败"
      setMenuError(msg)
      toast.error(msg)
    } finally {
      setMenuLoading(false)
    }
  }

  const handleDelete = async () => {
    if (!tokens?.accessToken) return
    if (!window.confirm("确认删除这篇知文吗？删除后不可恢复")) return
    setMenuLoading(true)
    try {
      await knowpostService.remove(id, tokens.accessToken)
      setMenuOpen(false)
      onChanged?.("delete")
    } catch (e) {
      setMenuError(e instanceof Error ? e.message : "删除失败")
    } finally {
      setMenuLoading(false)
    }
  }

  const content = (
    <>
      <div className="overflow-hidden rounded-xl">
        {coverImage ? (
          <img
            className="aspect-[4/3] w-full object-cover"
            src={coverImage}
            alt={title}
            loading="lazy"
          />
        ) : (
          <CoverPlaceholder title={title} tag={tags[0]} />
        )}
      </div>
      <div className="flex flex-1 flex-col gap-2">
        <h3 className="line-clamp-2 min-h-[2.5rem] font-semibold leading-snug">
          {title}
        </h3>
        <p className="line-clamp-2 min-h-[2.5rem] text-sm text-muted-foreground">
          {summary.trim() || "点击查看正文详情"}
        </p>
        {tags.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {tags.slice(0, 3).map((tag) => (
              <span
                key={tag}
                className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary"
              >
                #{tag}
              </span>
            ))}
          </div>
        )}
      </div>
      <div className="mt-auto flex items-center gap-2 pt-2">
        <UserAvatar
          src={teacher.avatarUrl}
          nickname={teacher.name}
          className="size-7"
        />
        <div className="flex flex-col">
          <span className="text-xs font-medium">{teacher.name}</span>
          {authorTags && authorTags.length > 0 && (
            <span className="text-[11px] text-muted-foreground">
              {authorTags.map((t) => `#${t}`).join(" ")}
            </span>
          )}
        </div>
        {localVisible && (
          <span
            className={`ml-auto inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ${
              localVisible === "public"
                ? "bg-emerald-100 text-emerald-700"
                : "bg-slate-200 text-slate-600"
            }`}
          >
            {localVisible === "public" ? <Globe className="size-3" /> : <Lock className="size-3" />}
            {localVisible === "public" ? "公开" : "私密"}
          </span>
        )}
      </div>
      {footerExtra && <div className="pt-1">{footerExtra}</div>}
    </>
  )

  return (
    <motion.article
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
      whileHover={{ y: -4 }}
      className={`glass-surface glass-border relative flex h-full flex-col gap-3 rounded-2xl p-4 ${className ?? ""}`}
    >
      {localIsTop && (
        <div className="absolute left-3 top-3 z-[2]">
          <span className="inline-flex items-center gap-1 rounded-full bg-gradient-to-r from-amber-400 to-orange-500 px-2 py-0.5 text-[11px] font-bold text-white shadow-sm">
            <Pin className="size-3" />
            置顶
          </span>
        </div>
      )}
      {editable && (
        <>
          <button
            ref={btnRef}
            type="button"
            className="absolute right-3 top-3 z-[5] flex size-7 items-center justify-center rounded-full border border-white/60 bg-white/70 text-slate-500 backdrop-blur-md transition-colors hover:bg-white/90"
            onClick={() => setMenuOpen(!menuOpen)}
          >
            <MoreHorizontal className="size-4" />
          </button>
          {menuOpen && (
            <div
              ref={menuRef}
              className="glass-surface absolute right-3 top-11 z-10 min-w-[140px] rounded-xl border border-white/60 p-1"
            >
              {menuError && (
                <div className="px-2 py-1 text-xs text-destructive">
                  {menuError}
                </div>
              )}
              <button
                className="flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-sm hover:bg-muted"
                onClick={() => handleSetTop(!localIsTop)}
                disabled={menuLoading}
              >
                <Pin className="size-3.5" />
                {localIsTop ? "取消置顶" : "置顶"}
              </button>
              <button
                className="flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-sm hover:bg-muted"
                onClick={() => handleSetVisibility("public")}
                disabled={menuLoading}
              >
                <Globe className="size-3.5" />
                设为公开
              </button>
              <button
                className="flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-sm hover:bg-muted"
                onClick={() => handleSetVisibility("private")}
                disabled={menuLoading}
              >
                <Lock className="size-3.5" />
                设为私密
              </button>
              <Link
                className="flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-sm hover:bg-muted"
                href={`/app/posts/create?editId=${id}`}
                onClick={() => setMenuOpen(false)}
              >
                <Pencil className="size-3.5" />
                修改
              </Link>
              <div className="my-1 h-px bg-border" />
              <button
                className="flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-sm text-destructive hover:bg-destructive/10"
                onClick={handleDelete}
                disabled={menuLoading}
              >
                <Trash2 className="size-3.5" />
                删除
              </button>
            </div>
          )}
        </>
      )}
      {to ? (
        <Link href={to} className="flex flex-1 flex-col gap-3">
          {content}
        </Link>
      ) : (
        content
      )}
    </motion.article>
  )
}
