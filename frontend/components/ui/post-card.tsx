"use client"

import { useRef, useState, useEffect } from "react"
import type { ReactNode } from "react"
import Link from "next/link"
import { MoreHorizontal, Pin, Globe, Lock, Trash2, Pencil } from "lucide-react"
import { useAuth } from "@/components/auth/auth-context"
import { knowpostService } from "@/lib/api/knowpost"
import type { VisibleScope } from "@/lib/types/knowpost"
import { UserAvatar } from "./user-avatar"

export type PostCardProps = {
  id: string
  title: string
  summary: string
  tags: string[]
  authorTags?: string[]
  isTop?: boolean
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
}

export function PostCard({
  id,
  title,
  summary,
  tags,
  authorTags,
  isTop,
  teacher,
  coverImage,
  to,
  footerExtra,
  editable = false,
  onChanged,
}: PostCardProps) {
  const { tokens } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuLoading, setMenuLoading] = useState(false)
  const [menuError, setMenuError] = useState<string | null>(null)
  const [localIsTop, setLocalIsTop] = useState(isTop)
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

  const handleSetVisibility = async (visible: VisibleScope) => {
    if (!tokens?.accessToken) return
    setMenuLoading(true)
    try {
      await knowpostService.setVisibility(id, visible, tokens.accessToken)
      setMenuOpen(false)
      onChanged?.("visibility", { visible })
    } catch (e) {
      setMenuError(e instanceof Error ? e.message : "操作失败")
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
      {coverImage && (
        <div className="overflow-hidden rounded-xl">
          <img
            className="aspect-[4/3] w-full object-cover"
            src={coverImage}
            alt={title}
            loading="lazy"
          />
        </div>
      )}
      <div className="flex flex-1 flex-col gap-2">
        <h3 className="line-clamp-2 font-semibold leading-snug">{title}</h3>
        {summary.trim() && (
          <p className="line-clamp-2 text-sm text-muted-foreground">
            {summary}
          </p>
        )}
        {tags.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {tags.map((tag) => (
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
      </div>
      {footerExtra && <div className="pt-1">{footerExtra}</div>}
    </>
  )

  return (
    <article className="relative flex flex-col gap-3 rounded-2xl border bg-background p-4 shadow-sm transition-shadow hover:shadow-md">
      {localIsTop && (
        <div className="absolute left-3 top-3 z-[2]">
          <span className="inline-flex items-center gap-1 rounded-full bg-primary px-2 py-0.5 text-[11px] font-bold text-primary-foreground">
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
            className="absolute right-3 top-3 z-[5] flex size-7 items-center justify-center rounded-full bg-muted/60 text-muted-foreground hover:bg-muted"
            onClick={() => setMenuOpen(!menuOpen)}
          >
            <MoreHorizontal className="size-4" />
          </button>
          {menuOpen && (
            <div
              ref={menuRef}
              className="absolute right-3 top-11 z-10 min-w-[140px] rounded-lg border bg-popover p-1 shadow-lg"
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
    </article>
  )
}
