import { apiFetch } from "./client"
import type { PresignRequest, PresignResponse } from "@/lib/types/knowpost"

export const storageService = {
  presign: (payload: PresignRequest) =>
    apiFetch<PresignResponse>("/api/storage/presign", {
      method: "POST",
      body: payload,
    }),

  /** 聊天图片受限表单直传（不依赖 postId）。 */
  presignChat: (payload: { contentType: string; ext?: string; size: number }) =>
    apiFetch<PresignResponse>("/api/storage/presign-chat", {
      method: "POST",
      body: payload,
    }),
}

export async function uploadToPresignedUrl(
  presign: PresignResponse,
  file: Blob,
) {
  const form = new FormData()
  Object.entries(presign.formFields).forEach(([key, value]) => form.append(key, value))
  form.append("file", file)
  await fetch(presign.putUrl, {
    method: presign.method,
    body: form,
  })
}

/**
 * 上传聊天图片到 OSS 并返回短期私有读 URL。
 * 失败抛错（由调用方 toast）。
 */
export async function uploadChatImage(file: File): Promise<string> {
  const contentType = file.type || "image/jpeg"
  const match = file.name.match(/\.[^.]+$/)
  const ext = match ? match[0] : ""
  const presign = await storageService.presignChat({ contentType, ext, size: file.size })

  const form = new FormData()
  Object.entries(presign.formFields).forEach(([key, value]) => form.append(key, value))
  form.append("file", file)
  const resp = await fetch(presign.putUrl, {
    method: presign.method,
    body: form,
    credentials: "omit",
  })
  if (!resp.ok) {
    const text = await resp.text().catch(() => "")
    throw new Error(text || `图片上传失败：${resp.status}`)
  }
  return presign.readUrl
}
