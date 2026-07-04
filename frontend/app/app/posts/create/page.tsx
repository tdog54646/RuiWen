"use client"

import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { Suspense, useEffect, useRef, useState } from "react"
import { TagInput } from "@/components/ui/tag-input"
import { SlideButton } from "@/components/ui/slide-button"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useAuth } from "@/components/auth/auth-context"
import {
  knowpostService,
  uploadToPresigned,
  computeSha256,
  ensureHttps,
  withCacheBuster,
} from "@/lib/api/knowpost"
import { X } from "lucide-react"
import dynamic from "next/dynamic"

const DynamicEditor = dynamic(
  () => import("@/components/ui/advanced-markdown-editor"),
  {
    ssr: false,
    loading: () => (
      <div className="min-h-[600px] w-full flex items-center justify-center border rounded-md bg-muted/50 text-sm text-muted-foreground">
        编辑器加载中...
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
      fallback={(
        <div className="flex flex-col gap-2 rounded-2xl bg-background/90 p-6 shadow-sm">
          <h1 className="text-2xl font-bold tracking-tight">创建新内容</h1>
          <p className="text-sm text-muted-foreground">加载中…</p>
        </div>
      )}
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

  if (isLoading) {
    return (
      <div className="flex flex-col gap-2 rounded-2xl bg-background/90 p-6 shadow-sm">
        <h1 className="text-2xl font-bold tracking-tight">{pageTitle}</h1>
        <p className="text-sm text-muted-foreground">正在检查登录状态…</p>
      </div>
    )
  }

  if (!tokens?.accessToken) {
    return (
      <div className="flex flex-col gap-6 rounded-2xl bg-background/90 p-6 shadow-sm">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight">{pageTitle}</h1>
          <p className="text-sm text-muted-foreground">{pageSubtitle}</p>
        </div>

        <section className="rounded-xl border p-4">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">登录后可继续操作</p>
            <Link href={`/login?next=${encodeURIComponent(loginNext)}`}>
              <Button size="sm">去登录</Button>
            </Link>
          </div>
        </section>
      </div>
    )
  }

  if (isEditMode && editLoading) {
    return (
      <div className="flex flex-col gap-6 rounded-2xl bg-background/90 p-6 shadow-sm">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight">{pageTitle}</h1>
          <p className="text-sm text-muted-foreground">{pageSubtitle}</p>
        </div>
        <div className="rounded-xl border p-4 text-sm text-muted-foreground">
          加载中…
        </div>
      </div>
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
        setUploadedImages((prev) => [...prev, { ossUrl, previewUrl: localPreview, localPreview: true }])
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
    <div className="flex flex-col gap-6 rounded-2xl bg-background/90 p-6 shadow-sm">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight">{pageTitle}</h1>
        <p className="text-sm text-muted-foreground">{pageSubtitle}</p>
      </div>

      <div className="flex flex-col gap-6">
        <h2 className="text-lg font-semibold">基本信息</h2>
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="title">标题 *</Label>
            <Input
              id="title"
              placeholder="输入内容标题"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>

          <div className="space-y-2 md:col-span-2">
            <Label>图片（多选）</Label>
            <div
              className="flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed p-8 text-center text-sm text-muted-foreground hover:border-primary/40"
              onClick={() => {
                if (uploadedImages.length >= MAX_IMAGES) {
                  setError(`最多可选择 ${MAX_IMAGES} 张图片`)
                  return
                }
                fileInputRef.current?.click()
              }}
            >
              <span>
                {imageUploading ? "正在上传…" : "点击上传图片"}
              </span>
              <small>
                支持 JPG / PNG / SVG，最多 {MAX_IMAGES} 张（已选{" "}
                {uploadedImages.length} / {MAX_IMAGES}）
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
              <div className="grid grid-cols-4 gap-3">
                {uploadedImages.map((img, idx) => (
                  <div key={idx} className="group relative">
                    <img
                      src={img.previewUrl}
                      alt=""
                      className="aspect-[3/4] w-full cursor-pointer rounded-lg object-cover"
                      onClick={() => setPreviewUrl(img.previewUrl)}
                    />
                    <button
                      type="button"
                      className="absolute right-1 top-1 flex size-5 items-center justify-center rounded-full bg-black/60 text-white opacity-0 transition-opacity group-hover:opacity-100"
                      onClick={() => removeImage(idx)}
                    >
                      <X className="size-3" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="space-y-2 md:col-span-2">
            <Label htmlFor="content">内容正文 *</Label>
            <DynamicEditor
              key={isEditMode ? `edit-${editId}` : "create"}
              initialValue={content}
              onChange={(val) => setContent(val)}
            />
          </div>

          <div className="space-y-2 md:col-span-2">
            <div className="flex items-center justify-between">
              <Label htmlFor="summary">知识摘要</Label>
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <span>AI 摘要</span>
                <button
                  type="button"
                  className={`relative h-5 w-9 rounded-full transition-colors ${aiSummaryEnabled ? "bg-primary" : "bg-muted"}`}
                  onClick={handleToggleAiSummary}
                >
                  <span
                    className={`absolute top-0.5 size-4 rounded-full bg-white shadow transition-transform ${aiSummaryEnabled ? "translate-x-4" : "translate-x-0.5"}`}
                  />
                </button>
                {aiSummaryLoading && (
                  <small className="text-muted-foreground">生成中…</small>
                )}
              </div>
            </div>
            <textarea
              id="summary"
              className="min-h-[80px] w-full resize-y rounded-lg border bg-background p-3 text-sm outline-none focus:border-ring"
              placeholder="填写内容摘要（50字以内）"
              value={summary}
              onChange={(e) => setSummary(e.target.value)}
            />
            <small
              className={`text-xs ${summary.trim().length > 50 ? "text-destructive" : "text-muted-foreground"}`}
            >
              {summary.trim().length} / 50
            </small>
          </div>

          <div className="space-y-2">
            <Label>标签</Label>
            <TagInput
              value={tags}
              onChange={setTags}
              placeholder="输入标签后按回车"
            />
          </div>

          <div className="space-y-2 md:col-span-2">
            <div
              className="flex cursor-pointer items-center justify-between rounded-xl bg-muted/50 px-4 py-3"
              onClick={() => setVisiblePublic((prev) => !prev)}
            >
              <div>
                <div className="text-sm font-medium">可见范围</div>
                <small className="text-muted-foreground">
                  {visiblePublic ? "公开" : "私密"}
                </small>
              </div>
              <div
                className={`relative h-6 w-11 rounded-full transition-colors ${visiblePublic ? "bg-primary" : "bg-muted"}`}
              >
                <span
                  className={`absolute top-0.5 size-5 rounded-full bg-white shadow transition-transform ${visiblePublic ? "translate-x-5" : "translate-x-0.5"}`}
                />
              </div>
            </div>
          </div>
        </div>

        <div className="flex justify-end gap-3">
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
            aria-label={submitting ? (isEditMode ? "保存中" : "发布中") : (isEditMode ? "滑动保存" : "滑动发布")}
          />
        </div>
        {error && (
          <div className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {error}
          </div>
        )}
        {message && (
          <div className="rounded-lg bg-green-50 px-3 py-2 text-sm text-green-700 dark:bg-green-950/30 dark:text-green-300">
            {message}
          </div>
        )}
        {previewUrl && (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
            onClick={() => setPreviewUrl(null)}
          >
            <img
              src={previewUrl}
              className="max-h-[90vh] max-w-[90vw] rounded-xl shadow-xl"
              alt="预览"
            />
          </div>
        )}
      </div>
    </div>
  )
}
