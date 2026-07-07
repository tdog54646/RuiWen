"use client"

import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { Suspense, useEffect, useRef, useState } from "react"
import { TagInput } from "@/components/ui/tag-input"
import { SlideButton } from "@/components/ui/slide-button"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { useAuth } from "@/components/auth/auth-context"
import {
  knowpostService,
  uploadToPresigned,
  computeSha256,
  ensureHttps,
  withCacheBuster,
} from "@/lib/api/knowpost"
import { AnimatePresence, motion } from "framer-motion"
import {
  GlassCard,
  StatusChip,
  StudioShell,
  Toggle,
} from "@/components/ui/studio"
import { cn } from "@/lib/utils"
import {
  AlertCircle,
  CheckCircle2,
  FileText,
  ImagePlus,
  Lock,
  Globe2,
  Sparkles,
  X,
} from "lucide-react"
import dynamic from "next/dynamic"

const DynamicEditor = dynamic(
  () => import("@/components/ui/advanced-markdown-editor"),
  {
    ssr: false,
    loading: () => (
      <div className="flex min-h-[600px] w-full items-center justify-center rounded-xl bg-white/40 text-sm text-slate-400 backdrop-blur">
        <span className="studio-shimmer rounded-full px-4 py-1.5 text-transparent">
          编辑器加载中
        </span>
      </div>
    ),
  }
)

type UploadedImage = {
  ossUrl: string
  previewUrl: string
  localPreview?: boolean
}

export default function CreatePage() {
  return (
    <Suspense
      fallback={
        <StudioShell>
          <div className="glass-surface glass-border relative rounded-2xl p-8">
            <h1 className="text-gradient text-2xl font-bold tracking-tight">
              创建新内容
            </h1>
            <p className="mt-1 text-sm text-slate-500">加载中…</p>
          </div>
        </StudioShell>
      }
    >
      <CreatePageContent />
    </Suspense>
  )
}

function CreatePageContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const editId = searchParams.get("editId")
  const isEditMode = Boolean(editId)
  const { tokens, isLoading } = useAuth()
  const [tags, setTags] = useState<string[]>([])
  const [title, setTitle] = useState("")
  const [content, setContent] = useState("")
  const [visiblePublic, setVisiblePublic] = useState(true)
  const [isTop, setIsTop] = useState(false)
  const [summary, setSummary] = useState("")
  const [aiSummaryEnabled, setAiSummaryEnabled] = useState(false)
  const [aiSummaryLoading, setAiSummaryLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [postId, setPostId] = useState<string | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [editLoading, setEditLoading] = useState(false)

  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const [imageUploading, setImageUploading] = useState(false)
  const [uploadedImages, setUploadedImages] = useState<UploadedImage[]>([])
  const MAX_IMAGES = 15

  useEffect(() => {
    if (!editId || !tokens?.accessToken) return

    let cancelled = false
    setEditLoading(true)
    setError(null)
    setMessage(null)
    setPostId(editId)

    knowpostService
      .detail(editId, tokens.accessToken)
      .then(async (resp) => {
        if (cancelled) return
        setTitle(resp.title ?? "")
        setTags(resp.tags ?? [])
        setSummary(resp.description ?? "")
        setVisiblePublic(resp.visible === "public")
        setIsTop(Boolean(resp.isTop))
        setUploadedImages(
          (resp.images ?? []).map((url) => ({
            ossUrl: ensureHttps(url),
            previewUrl: ensureHttps(url),
          })),
        )
        if (resp.contentUrl) {
          const text = await fetch(withCacheBuster(resp.contentUrl), {
            credentials: "omit",
          }).then((r) => {
            if (!r.ok) throw new Error(`HTTP ${r.status}`)
            return r.text()
          })
          if (!cancelled) setContent(text)
        }
      })
      .catch((err) => {
        if (cancelled) {
          setError(err instanceof Error ? err.message : "加载知文失败")
        }
      })
      .finally(() => {
        if (!cancelled) setEditLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [editId, tokens?.accessToken])

  const pageTitle = isEditMode ? "修改知文" : "创建新内容"
  const pageSubtitle = isEditMode ? "继续完善这篇知文" : "分享你的知识，让更多人受益"
  const loginNext = isEditMode && editId
    ? `/app/posts/create?editId=${editId}`
    : "/app/posts/create"
  const charCount = content.length

  if (isLoading) {
    return (
      <StudioShell>
        <CenterCard title={pageTitle}>
          <p className="text-sm text-slate-500">正在检查登录状态…</p>
        </CenterCard>
      </StudioShell>
    )
  }

  if (!tokens?.accessToken) {
    return (
      <StudioShell>
        <CenterCard title={pageTitle} subtitle={pageSubtitle}>
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-slate-500">登录后可继续操作</p>
            <Link href={`/login?next=${encodeURIComponent(loginNext)}`}>
              <Button size="sm">去登录</Button>
            </Link>
          </div>
        </CenterCard>
      </StudioShell>
    )
  }

  if (isEditMode && editLoading) {
    return (
      <StudioShell>
        <CenterCard title={pageTitle} subtitle={pageSubtitle}>
          <div className="text-sm text-slate-500">加载中…</div>
        </CenterCard>
      </StudioShell>
    )
  }

  const ensureDraft = async (): Promise<string> => {
    if (editId) {
      setPostId(editId)
      return editId
    }
    if (postId) return postId
    const resp = await knowpostService.createDraft()
    const idStr = String(resp.id)
    setPostId(idStr)
    setMessage(`草稿已创建：${idStr}`)
    return idStr
  }

  const handleSelectImages = async (files: FileList | null) => {
    if (!files || files.length === 0) return
    setError(null)
    setMessage(null)
    setImageUploading(true)
    try {
      const id = await ensureDraft()
      const remaining = Math.max(0, MAX_IMAGES - uploadedImages.length)
      if (remaining <= 0) {
        setError(`最多可选择 ${MAX_IMAGES} 张图片`)
        return
      }
      const allSelected = Array.from(files)
      const arr = allSelected.slice(0, remaining)
      for (const f of arr) {
        const match = f.name.match(/\.[^.]+$/)
        const ext = match ? match[0] : ".jpg"
        const contentType = f.type || "image/jpeg"
        const presign = await knowpostService.presign({
          scene: "knowpost_image",
          postId: id,
          contentType,
          ext,
        })
        await uploadToPresigned(presign.putUrl, presign.headers, f)
        const ossUrl = ensureHttps(presign.putUrl).split("?")[0]
        const localPreview = URL.createObjectURL(f)
        setUploadedImages((prev) => [
          ...prev,
          { ossUrl, previewUrl: localPreview, localPreview: true },
        ])
      }
      const ignored = allSelected.length - arr.length
      setMessage(
        `图片上传成功：${arr.length} 张${ignored > 0 ? `（已超过上限，忽略 ${ignored} 张）` : ""}`,
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : "图片上传失败")
    } finally {
      setImageUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ""
    }
  }

  const removeImage = (index: number) => {
    setUploadedImages((prev) => {
      const removed = prev[index]
      if (removed?.localPreview) URL.revokeObjectURL(removed.previewUrl)
      return prev.filter((_, i) => i !== index)
    })
  }

  const handlePublish = async (): Promise<boolean> => {
    setMessage(null)
    setError(null)
    if (!title.trim()) {
      setError("请填写标题")
      return false
    }
    if (!content.trim()) {
      setError("请填写内容正文")
      return false
    }
    if (summary.trim().length > 50) {
      setError("摘要不能超过50字")
      return false
    }
    setSubmitting(true)
    try {
      const id = await ensureDraft()
      const file = new File([content], "content.md", {
        type: "text/markdown",
      })
      const size = file.size
      const sha256 = await computeSha256(file)
      const presign = await knowpostService.presign({
        scene: "knowpost_content",
        postId: id,
        contentType: "text/markdown",
        ext: ".md",
      })
      const { etag } = await uploadToPresigned(
        presign.putUrl,
        presign.headers,
        file,
      )
      const imgUrls = uploadedImages.map((img) => img.ossUrl)
      if (isEditMode) {
        await knowpostService.saveEdit(id, {
          objectKey: presign.objectKey,
          etag,
          size,
          sha256,
          title: title.trim(),
          tags,
          imgUrls,
          visible: visiblePublic ? "public" : "private",
          isTop,
          description: summary.trim(),
        })
        setMessage("保存成功")
        router.push(`/app/posts/${id}`)
        return true
      }
      await knowpostService.confirmContent(id, {
        objectKey: presign.objectKey,
        etag,
        size,
        sha256,
      })
      await knowpostService.update(id, {
        title: title.trim(),
        tags: tags.length ? tags : undefined,
        imgUrls: imgUrls.length ? imgUrls : undefined,
        visible: visiblePublic ? "public" : "private",
        isTop: false,
        description: summary.trim() || undefined,
      })
      await knowpostService.publish(id)
      setMessage("发布成功 ✅")
      return true
    } catch (err) {
      setError(err instanceof Error ? err.message : isEditMode ? "保存失败" : "发布失败")
      return false
    } finally {
      setSubmitting(false)
    }
  }

  const handleToggleAiSummary = async () => {
    if (!aiSummaryEnabled) {
      if (!tokens?.accessToken) {
        setError("请先登录以使用 AI 摘要")
        return
      }
      if (!content.trim()) {
        setError("正文为空，无法生成摘要")
        return
      }
      setAiSummaryLoading(true)
      setMessage(null)
      setError(null)
      try {
        const resp = await knowpostService.suggestDescription(
          content,
          tokens.accessToken,
        )
        setSummary((resp.description ?? "").slice(0, 50))
        setAiSummaryEnabled(true)
        setMessage("AI 摘要已生成")
      } catch (err) {
        setError(err instanceof Error ? err.message : "生成失败")
      } finally {
        setAiSummaryLoading(false)
      }
    } else {
      setAiSummaryEnabled(false)
    }
  }

  return (
    <StudioShell>
      {/* Hero */}
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
        className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between"
      >
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <span
              className={cn(
                "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-medium backdrop-blur",
                isEditMode
                  ? "bg-amber-100/70 text-amber-700"
                  : "bg-violet-100/70 text-violet-700",
              )}
            >
              {isEditMode ? (
                <FileText className="size-3" />
              ) : (
                <Sparkles className="size-3" />
              )}
              {isEditMode ? "编辑模式" : "创作模式"}
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/60 px-2.5 py-1 text-[11px] text-slate-500 backdrop-blur">
              <span
                className={cn(
                  "size-1.5 rounded-full",
                  postId ? "bg-emerald-400 studio-pulse" : "bg-slate-300",
                )}
              />
              {postId ? `草稿 #${postId.slice(-6)}` : "尚未生成草稿"}
            </span>
          </div>
          <div>
            <h1 className="text-gradient text-3xl font-bold tracking-tight md:text-4xl">
              {pageTitle}
            </h1>
            <p className="mt-1.5 text-sm text-slate-500">{pageSubtitle}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <StatusChip icon={FileText}>{charCount} 字</StatusChip>
          <StatusChip icon={ImagePlus}>
            {uploadedImages.length}/{MAX_IMAGES}
          </StatusChip>
        </div>
      </motion.div>

      {/* Bento grid */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        {/* 标题 */}
        <GlassCard className="lg:col-span-8" delay={0.05}>
          <Label className="text-[11px] font-medium uppercase tracking-wider text-slate-400">
            标题
          </Label>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="为你的知文起一个标题…"
            className="mt-2 w-full bg-transparent text-2xl font-semibold tracking-tight text-slate-900 outline-none placeholder:font-normal placeholder:text-slate-300"
          />
        </GlassCard>

        {/* 封面图 */}
        <GlassCard className="lg:col-span-4" delay={0.1}>
          <Label className="text-[11px] font-medium uppercase tracking-wider text-slate-400">
            封面图片
          </Label>
          <div
            onClick={() => {
              if (uploadedImages.length >= MAX_IMAGES) {
                setError(`最多可选择 ${MAX_IMAGES} 张图片`)
                return
              }
              fileInputRef.current?.click()
            }}
            className="group/drop mt-2 flex min-h-[124px] cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-slate-300 bg-white/40 text-center transition-colors hover:border-cyan-400/70 hover:bg-cyan-50/40"
          >
            <div className="flex size-10 items-center justify-center rounded-full bg-gradient-to-br from-cyan-400/20 to-violet-500/20 text-violet-600 transition-transform group-hover/drop:scale-110">
              <ImagePlus className="size-5" />
            </div>
            <span className="text-sm font-medium text-slate-700">
              {imageUploading ? "上传中…" : "点击上传图片"}
            </span>
            <small className="text-xs text-slate-400">
              JPG · PNG · SVG · {uploadedImages.length}/{MAX_IMAGES}
            </small>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              multiple
              className="hidden"
              onChange={(e) => handleSelectImages(e.target.files)}
            />
          </div>
          {uploadedImages.length > 0 && (
            <div className="mt-3 grid grid-cols-4 gap-2">
              {uploadedImages.map((img, idx) => (
                <motion.div
                  key={idx}
                  initial={{ opacity: 0, scale: 0.85 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: idx * 0.03 }}
                  className="group relative"
                >
                  <img
                    src={img.previewUrl}
                    alt=""
                    className="aspect-square w-full cursor-pointer rounded-lg object-cover ring-1 ring-black/5"
                    onClick={() => setPreviewUrl(img.previewUrl)}
                  />
                  <button
                    type="button"
                    className="absolute right-1 top-1 flex size-5 items-center justify-center rounded-full bg-black/55 text-white opacity-0 backdrop-blur transition-opacity group-hover:opacity-100"
                    onClick={() => removeImage(idx)}
                  >
                    <X className="size-3" />
                  </button>
                </motion.div>
              ))}
            </div>
          )}
        </GlassCard>

        {/* 正文编辑器（主舞台） */}
        <GlassCard
          className="lg:col-span-8"
          delay={0.15}
          disableHover
          contentClassName="flex flex-col"
        >
          <div className="mb-3 flex items-center justify-between">
            <Label className="text-[11px] font-medium uppercase tracking-wider text-slate-400">
              内容正文
            </Label>
            <span className="text-[11px] text-slate-400">Markdown · 实时预览</span>
          </div>
          <DynamicEditor
            key={isEditMode ? `edit-${editId}` : "create"}
            initialValue={content}
            onChange={(val) => setContent(val)}
          />
        </GlassCard>

        {/* 右侧控制栏 */}
        <div className="flex flex-col gap-4 lg:col-span-4">
          {/* 知识摘要 */}
          <GlassCard
            className="flex-1"
            delay={0.2}
            contentClassName="flex h-full flex-col"
          >
            <div className="mb-3 flex items-center justify-between">
              <Label className="text-[11px] font-medium uppercase tracking-wider text-slate-400">
                知识摘要
              </Label>
              <div className="flex items-center gap-2">
                <Sparkles
                  className={cn(
                    "size-3.5 transition-colors",
                    aiSummaryEnabled ? "text-violet-500" : "text-slate-300",
                  )}
                />
                <span className="text-xs text-slate-500">AI 摘要</span>
                <Toggle
                  checked={aiSummaryEnabled}
                  loading={aiSummaryLoading}
                  onChange={handleToggleAiSummary}
                />
              </div>
            </div>
            <textarea
              id="summary"
              className="min-h-[80px] flex-1 w-full resize-y rounded-xl border border-white/60 bg-white/50 p-3 text-sm outline-none transition-colors placeholder:text-slate-400 focus:border-cyan-400/60 focus:bg-white/70"
              placeholder="填写内容摘要（50字以内）"
              value={summary}
              onChange={(e) => setSummary(e.target.value)}
            />
            <div className="mt-2 flex items-center justify-between text-[11px]">
              <span
                className={cn(
                  summary.trim().length > 50
                    ? "text-destructive"
                    : "text-slate-400",
                )}
              >
                {summary.trim().length} / 50
              </span>
              {aiSummaryLoading && (
                <span className="studio-shimmer rounded-full px-2 py-0.5 text-transparent">
                  AI 生成中
                </span>
              )}
            </div>
          </GlassCard>

          {/* 标签 */}
          <GlassCard delay={0.25}>
            <Label className="text-[11px] font-medium uppercase tracking-wider text-slate-400">
              标签
            </Label>
            <div className="mt-2">
              <TagInput
                value={tags}
                onChange={setTags}
                placeholder="输入标签后按回车"
              />
            </div>
          </GlassCard>

          {/* 可见范围 */}
          <GlassCard delay={0.3}>
            <div
              className="flex cursor-pointer items-center justify-between"
              onClick={() => setVisiblePublic((prev) => !prev)}
            >
              <div className="flex items-center gap-3">
                <div className="flex size-9 items-center justify-center rounded-lg bg-gradient-to-br from-cyan-400/20 to-violet-500/20 text-violet-600">
                  {visiblePublic ? (
                    <Globe2 className="size-4" />
                  ) : (
                    <Lock className="size-4" />
                  )}
                </div>
                <div>
                  <div className="text-sm font-medium text-slate-800">
                    可见范围
                  </div>
                  <small className="text-xs text-slate-400">
                    {visiblePublic ? "公开 · 任何人可见" : "私密 · 仅自己可见"}
                  </small>
                </div>
              </div>
              <Toggle
                checked={visiblePublic}
                interactive={false}
                onChange={() => setVisiblePublic((prev) => !prev)}
              />
            </div>
          </GlassCard>
        </div>
      </div>

      {/* 消息条 */}
      <div className="flex flex-col gap-2">
        <AnimatePresence>
          {error && (
            <motion.div
              initial={{ opacity: 0, y: -8, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -8, scale: 0.98 }}
              transition={{ type: "spring", stiffness: 400, damping: 30 }}
              className="flex items-center gap-2 rounded-xl border border-red-200/60 bg-red-50/70 px-4 py-2.5 text-sm text-red-600 backdrop-blur-md"
            >
              <AlertCircle className="size-4 shrink-0" />
              {error}
            </motion.div>
          )}
        </AnimatePresence>
        <AnimatePresence>
          {message && (
            <motion.div
              initial={{ opacity: 0, y: -8, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -8, scale: 0.98 }}
              transition={{ type: "spring", stiffness: 400, damping: 30 }}
              className="flex items-center gap-2 rounded-xl border border-emerald-200/60 bg-emerald-50/70 px-4 py-2.5 text-sm text-emerald-600 backdrop-blur-md"
            >
              <CheckCircle2 className="size-4 shrink-0" />
              {message}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* 滑动发布 CTA */}
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.35, duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
        className="flex flex-col items-center gap-3 pt-2"
      >
        <div className="glow-cta rounded-full">
          <SlideButton
            onSlideComplete={async () => {
              const ok = await handlePublish()
              if (!ok) {
                throw new Error(isEditMode ? "保存失败" : "发布失败")
              }
            }}
            disabled={submitting || imageUploading}
            resetOnSuccessMs={1500}
            idleText={isEditMode ? "滑动保存" : "滑动发布"}
            loadingText={isEditMode ? "保存中" : "发布中"}
            successText={isEditMode ? "已保存" : "已发布"}
            errorText={isEditMode ? "重试保存" : "重试发布"}
            aria-label={
              submitting
                ? isEditMode
                  ? "保存中"
                  : "发布中"
                : isEditMode
                  ? "滑动保存"
                  : "滑动发布"
            }
          />
        </div>
        <p className="text-xs text-slate-400">
          向右滑动 · {isEditMode ? "保存修改" : "发布你的知文"}
        </p>
      </motion.div>

      {/* 图片预览灯箱 */}
      <AnimatePresence>
        {previewUrl && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-md"
            onClick={() => setPreviewUrl(null)}
          >
            <motion.img
              initial={{ scale: 0.92, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.92, opacity: 0 }}
              transition={{ type: "spring", stiffness: 300, damping: 26 }}
              src={previewUrl}
              className="max-h-[90vh] max-w-[90vw] rounded-2xl shadow-2xl"
              alt="预览"
            />
          </motion.div>
        )}
      </AnimatePresence>
    </StudioShell>
  )
}

/* ----------------------------- 局部组件 ----------------------------- */

function CenterCard({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle?: string
  children: React.ReactNode
}) {
  return (
    <div className="glass-surface glass-border mx-auto mt-10 w-full max-w-md rounded-2xl p-6">
      <h1 className="text-gradient text-2xl font-bold tracking-tight">{title}</h1>
      {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      <div className="mt-5">{children}</div>
    </div>
  )
}
