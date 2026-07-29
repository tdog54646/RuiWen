"use client"

import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { Suspense, useEffect, useRef, useState } from "react"
import { TagInput } from "@/components/ui/tag-input"
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
import { StudioShell } from "@/components/ui/studio"
import { cn } from "@/lib/utils"
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
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
      <div className="flex min-h-[600px] w-full items-center justify-center rounded-xl bg-[#f3f3ee] text-sm text-[#777b76]">
        <span>编辑器加载中…</span>
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
          <div className="rounded-2xl bg-[#fbfbf8] p-8 shadow-[0_20px_50px_-42px_rgba(29,33,31,0.55)] ring-1 ring-[#deded8]">
            <h1 className="font-display text-3xl font-medium tracking-[-0.04em] text-[#1d211f]">
              创建新内容
            </h1>
            <p className="mt-2 text-sm text-[#70746f]">加载中…</p>
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
        if (!cancelled) {
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
      setMessage("发布成功")
      return true
    } catch (err) {
      setError(err instanceof Error ? err.message : isEditMode ? "保存失败" : "发布失败")
      return false
    } finally {
      setSubmitting(false)
    }
  }

  const handleGenerateAiSummary = async () => {
    if (!tokens?.accessToken) {
      setError("请先登录以使用自动摘要")
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
      setMessage("摘要已生成")
    } catch (err) {
      setError(err instanceof Error ? err.message : "生成失败")
    } finally {
      setAiSummaryLoading(false)
    }
  }

  return (
    <StudioShell className="creation-workspace pb-10">
      <header className="flex flex-col gap-5 py-4 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-4">
          <Link
            href="/app/profile"
            className="flex size-10 shrink-0 items-center justify-center rounded-lg text-[#626762] transition-colors hover:bg-[#e7e8e3] hover:text-[#1d211f] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
            aria-label="返回我的主页"
          >
            <ArrowLeft className="size-5" />
          </Link>
          <div>
            <h1 className="font-display text-3xl font-medium tracking-[-0.045em] text-[#1d211f]">
              {pageTitle}
            </h1>
            <p className="mt-1 text-sm text-[#70746f]">{pageSubtitle}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-4 md:justify-end">
          <span className="text-sm text-[#777b76]">
            {charCount} 字 · {uploadedImages.length} 张图片
            {postId ? ` · 草稿 ${postId.slice(-6)}` : ""}
          </span>
          <Button
            type="button"
            onClick={() => void handlePublish()}
            disabled={submitting || imageUploading}
            className="h-10 rounded-lg bg-[#1d211f] px-6 text-sm font-semibold text-white shadow-none hover:bg-[#2f5d50]"
          >
            {submitting
              ? isEditMode ? "保存中…" : "发布中…"
              : isEditMode ? "保存修改" : "发布知文"}
          </Button>
        </div>
      </header>

      <div className="grid grid-cols-1 items-start gap-5 lg:grid-cols-[minmax(0,1fr)_21rem]">
        <article className="overflow-hidden rounded-2xl bg-[#fbfbf8] shadow-[0_20px_55px_-45px_rgba(29,33,31,0.55)] ring-1 ring-[#deded8]">
        {/* 标题 */}
        <section className="px-6 pb-7 pt-8 md:px-8 md:pt-10">
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="为你的知文起一个标题…"
            aria-label="知文标题"
            className="font-display w-full bg-transparent text-4xl font-medium leading-tight tracking-[-0.05em] text-[#1d211f] outline-none placeholder:font-normal placeholder:text-[#a0a39e] md:text-5xl"
          />
          <p className="mt-4 text-sm text-[#858984]">用一句清楚的标题说明这篇内容讨论什么。</p>
        </section>

        {/* 正文编辑器（主舞台） */}
        <section className="flex flex-col bg-[#f8f8f5] p-4 md:p-5">
          <div className="mb-4 flex items-center justify-between gap-4 px-1">
            <Label className="text-sm font-semibold text-[#555a56]">
              内容正文
            </Label>
            <span className="text-sm text-[#858984]">Markdown 编辑与预览</span>
          </div>
          <DynamicEditor
            key={isEditMode ? `edit-${editId}` : "create"}
            initialValue={content}
            onChange={(val) => setContent(val)}
          />
        </section>
        </article>

        {/* 右侧控制栏 */}
        <aside className="flex flex-col gap-6 rounded-2xl bg-[#fbfbf8] p-5 shadow-[0_20px_55px_-45px_rgba(29,33,31,0.55)] ring-1 ring-[#deded8] lg:sticky lg:top-28">
          <h2 className="font-display text-2xl font-medium tracking-[-0.035em] text-[#1d211f]">发布设置</h2>
          <section>
            <Label className="text-sm font-semibold text-[#555a56]">封面与配图</Label>
            <button
              type="button"
              onClick={() => {
                if (uploadedImages.length >= MAX_IMAGES) {
                  setError(`最多可选择 ${MAX_IMAGES} 张图片`)
                  return
                }
                fileInputRef.current?.click()
              }}
              className="mt-3 flex min-h-28 w-full flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-[#cfd1ca] bg-[#f3f3ee] text-center transition-colors hover:border-[#8f9992] hover:bg-[#efefe9] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
            >
              <ImagePlus className="size-5 text-[#2f5d50]" />
              <span className="text-sm font-semibold text-[#4f5550]">
                {imageUploading ? "上传中…" : "添加图片"}
              </span>
              <span className="text-sm text-[#858984]">{uploadedImages.length}/{MAX_IMAGES}</span>
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              multiple
              className="hidden"
              onChange={(e) => handleSelectImages(e.target.files)}
            />
            {uploadedImages.length > 0 && (
              <div className="mt-3 grid grid-cols-4 gap-2">
                {uploadedImages.map((img, idx) => (
                  <motion.div
                    key={img.ossUrl}
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="group relative"
                  >
                    <img
                      src={img.previewUrl}
                      alt={`已上传图片 ${idx + 1}`}
                      className="aspect-square w-full cursor-pointer rounded-lg object-cover ring-1 ring-black/5"
                      onClick={() => setPreviewUrl(img.previewUrl)}
                    />
                    <button
                      type="button"
                      aria-label={`移除图片 ${idx + 1}`}
                      className="absolute right-1 top-1 flex size-5 items-center justify-center rounded-md bg-black/60 text-white opacity-0 transition-opacity group-hover:opacity-100 focus:opacity-100"
                      onClick={() => removeImage(idx)}
                    >
                      <X className="size-3" />
                    </button>
                  </motion.div>
                ))}
              </div>
            )}
          </section>

          {/* 知识摘要 */}
          <section className="flex flex-col">
            <div className="mb-4 flex items-center justify-between gap-4">
              <Label className="text-sm font-semibold text-[#555a56]">
                知识摘要
              </Label>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => void handleGenerateAiSummary()}
                disabled={aiSummaryLoading}
                className="h-8 gap-1.5 rounded-md px-2 text-[#4f5550] hover:bg-[#ecece6]"
              >
                <Sparkles className="size-4" />
                {aiSummaryLoading ? "生成中…" : "生成摘要"}
              </Button>
            </div>
            <textarea
              id="summary"
              className="min-h-32 w-full resize-y rounded-xl border border-[#d8d9d2] bg-[#f3f3ee] p-3 text-sm leading-6 text-[#343936] outline-none transition-colors placeholder:text-[#969994] focus:border-[#2f5d50] focus:bg-[#fbfbf8]"
              placeholder="填写内容摘要（50字以内）"
              value={summary}
              onChange={(e) => setSummary(e.target.value)}
            />
            <div className="mt-3 flex items-center justify-between text-sm">
              <span
                className={cn(
                  summary.trim().length > 50
                    ? "text-destructive"
                    : "text-[#858984]",
                )}
              >
                {summary.trim().length} / 50
              </span>
            </div>
          </section>

          {/* 标签 */}
          <section>
            <Label className="text-sm font-semibold text-[#555a56]">
              标签
            </Label>
            <div className="mt-2">
              <TagInput
                value={tags}
                onChange={setTags}
                placeholder="输入标签后按回车"
              />
            </div>
          </section>

          {/* 可见范围 */}
          <section>
            <Label className="text-sm font-semibold text-[#555a56]">可见范围</Label>
            <div className="mt-3 grid grid-cols-2 gap-1 rounded-xl bg-[#ecece6] p-1">
              <button
                type="button"
                aria-pressed={visiblePublic}
                onClick={() => setVisiblePublic(true)}
                className={cn(
                  "flex items-center justify-center gap-2 rounded-lg px-3 py-2.5 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]",
                  visiblePublic ? "bg-[#fbfbf8] text-[#263d35] shadow-sm" : "text-[#777b76] hover:text-[#343936]",
                )}
              >
                <Globe2 className="size-4" />
                公开
              </button>
              <button
                type="button"
                aria-pressed={!visiblePublic}
                onClick={() => setVisiblePublic(false)}
                className={cn(
                  "flex items-center justify-center gap-2 rounded-lg px-3 py-2.5 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]",
                  !visiblePublic ? "bg-[#fbfbf8] text-[#263d35] shadow-sm" : "text-[#777b76] hover:text-[#343936]",
                )}
              >
                <Lock className="size-4" />
                私密
              </button>
            </div>
          </section>
        </aside>
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
              className="flex items-center gap-2 border-l-2 border-red-500 bg-red-50/60 px-4 py-3 text-sm text-red-700"
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
              className="flex items-center gap-2 border-l-2 border-[#2f5d50] bg-[#e7eee9] px-4 py-3 text-sm text-[#2f5d50]"
            >
              <CheckCircle2 className="size-4 shrink-0" />
              {message}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

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
    <div className="mx-auto mt-10 w-full max-w-md rounded-2xl bg-[#fbfbf8] p-7 shadow-[0_20px_50px_-42px_rgba(29,33,31,0.55)] ring-1 ring-[#deded8]">
      <h1 className="font-display text-3xl font-medium tracking-[-0.04em] text-[#1d211f]">{title}</h1>
      {subtitle && <p className="mt-2 text-sm text-[#70746f]">{subtitle}</p>}
      <div className="mt-5">{children}</div>
    </div>
  )
}
