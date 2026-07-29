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
import { cn } from "@/lib/utils"
import { UserAvatar } from "./user-avatar"

/** 无封面图时使用统一的编辑部式占位封面，避免随机渐变干扰内容。 */
function CoverPlaceholder({ title }: { title: string }) {
  const initial = title.trim().charAt(0) || "知"
  return (
    <div className="post-cover-placeholder relative flex size-full min-h-56 items-center justify-center overflow-hidden">
      <span className="font-display relative select-none text-[clamp(4.5rem,9vw,8rem)] font-medium leading-none tracking-[-0.08em] text-[#e7e9df]">
        {initial}
      </span>
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
  featured?: boolean
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
  isTop,
  visible,
  teacher,
  coverImage,
  to,
  footerExtra,
  featured = false,
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
      <div
        className={cn(
          "overflow-hidden border-b border-[#deded8] bg-[#e8e9e3]",
          featured && "lg:border-b-0 lg:border-r",
        )}
      >
        {coverImage ? (
          <img
            className={cn(
              "w-full object-cover transition-transform duration-700 ease-out group-hover:scale-[1.025]",
              featured
                ? "aspect-[16/9] lg:h-full lg:min-h-[26rem] lg:aspect-auto"
                : "aspect-[4/3]",
            )}
            src={coverImage}
            alt={title}
            loading="lazy"
          />
        ) : (
          <CoverPlaceholder title={title} />
        )}
      </div>
      <div className={cn("flex flex-1 flex-col p-5", featured && "lg:p-7")}>
        {tags.length > 0 && (
          <div className="mb-4 flex flex-wrap gap-x-3 gap-y-1">
            {tags.slice(0, 3).map((tag) => (
              <span
                key={tag}
                className="text-sm font-medium text-[#557066]"
              >
                {tag}
              </span>
            ))}
          </div>
        )}
        <h3
          className={cn(
            "font-display text-balance font-medium leading-[1.18] tracking-[-0.035em] text-[#202522] transition-colors duration-200 group-hover:text-[#2f5d50]",
            featured ? "text-3xl lg:text-[2.5rem]" : "line-clamp-2 text-2xl",
          )}
        >
          {title}
        </h3>
        <p
          className={cn(
            "mt-3 text-pretty text-sm leading-6 text-[#717570]",
            featured ? "line-clamp-4" : "line-clamp-2 min-h-12",
          )}
        >
          {summary.trim() || "打开文章，阅读完整内容。"}
        </p>

        <div
          className={cn(
            "mt-auto flex items-center gap-2.5 pt-5",
            footerExtra && "pr-20",
          )}
        >
          <UserAvatar
            src={teacher.avatarUrl}
            nickname={teacher.name}
            className="size-7 rounded-lg"
          />
          <div className="min-w-0">
            <span className="truncate text-sm font-semibold text-[#3c413e]">
              {teacher.name}
            </span>
          </div>
          {localVisible && (
            <span
              className={cn(
                "ml-auto inline-flex items-center gap-1 text-sm font-medium",
                localVisible === "public"
                  ? "text-[#2f5d50]"
                  : "text-[#626761]",
              )}
            >
              {localVisible === "public" ? (
                <Globe className="size-3" />
              ) : (
                <Lock className="size-3" />
              )}
              {localVisible === "public" ? "公开" : "私密"}
            </span>
          )}
        </div>
      </div>
    </>
  )

  return (
    <motion.article
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
      className={cn(
        "group relative flex h-full flex-col overflow-hidden rounded-[1.25rem] border border-[#deded8] bg-[#fbfbf8] transition-[border-color,box-shadow,transform] duration-300 hover:-translate-y-1 hover:border-[#b9c2ba] hover:shadow-[0_22px_50px_-34px_rgba(37,54,46,0.55)]",
        className,
      )}
    >
      {localIsTop && (
        <div className="absolute left-3 top-3 z-[2]">
          <span className="inline-flex items-center gap-1 text-sm font-semibold text-white drop-shadow-md">
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
            aria-label="文章管理菜单"
            aria-expanded={menuOpen}
            className="absolute right-3 top-3 z-[5] flex size-8 items-center justify-center rounded-lg border border-[#d8d9d2] bg-[#fbfbf8]/90 text-[#666b66] backdrop-blur-md transition-colors hover:bg-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
            onClick={() => setMenuOpen(!menuOpen)}
          >
            <MoreHorizontal className="size-4" />
          </button>
          {menuOpen && (
            <div
              ref={menuRef}
              className="absolute right-3 top-12 z-10 min-w-[150px] rounded-xl border border-[#d8d9d2] bg-[#fbfbf8] p-1.5 shadow-[0_20px_45px_-25px_rgba(29,33,31,0.5)]"
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
        <Link
          href={to}
          className={cn(
            "flex flex-1 flex-col focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#2f5d50]",
            featured && "lg:grid lg:grid-cols-[1.18fr_0.82fr]",
          )}
        >
          {content}
        </Link>
      ) : (
        <div
          className={cn(
            "flex flex-1 flex-col",
            featured && "lg:grid lg:grid-cols-[1.18fr_0.82fr]",
          )}
        >
          {content}
        </div>
      )}
      {footerExtra && (
        <div
          className={cn(
            "absolute bottom-5 right-5 z-[3]",
            featured && "lg:bottom-7 lg:right-7",
          )}
        >
          {footerExtra}
        </div>
      )}
    </motion.article>
  )
}
