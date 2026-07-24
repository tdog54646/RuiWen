import { apiFetch } from "./client"
import type { PresignRequest, PresignResponse } from "@/lib/types/knowpost"

export const storageService = {
  presign: (payload: PresignRequest) =>
    apiFetch<PresignResponse>("/api/storage/presign", {
      method: "POST",
      body: payload,
    }),

  /** 聊天图片预签名直传（不依赖 postId）。 */
  presignChat: (payload: { contentType: string; ext?: string }) =>
    apiFetch<PresignResponse>("/api/storage/presign-chat", {
      method: "POST",
      body: payload,
    }),
}

export async function uploadToPresignedUrl(
  url: string,
  file: Blob,
  contentType: string,
) {
  await fetch(url, {
    method: "PUT",
    headers: { "Content-Type": contentType },
    body: file,
  })
}

/**
 * 上传聊天图片到 OSS 并返回公网 URL。
 * 流程：presign-chat -> PUT 预签名 URL -> 取 putUrl 去签名串得公网 URL。
 * 失败抛错（由调用方 toast）。
 */
export async function uploadChatImage(file: File): Promise<string> {
  const contentType = file.type || "image/jpeg"
  const match = file.name.match(/\.[^.]+$/)
  const ext = match ? match[0] : ""
  const presign = await storageService.presignChat({ contentType, ext })

  const resp = await fetch(presign.putUrl, {
    method: "PUT",
    headers: { "Content-Type": contentType },
    body: file,
    credentials: "omit",
  })
  if (!resp.ok) {
    const text = await resp.text().catch(() => "")
    throw new Error(text || `图片上传失败：${resp.status}`)
  }
  // OSS 桶为公共读；预签名 URL 去掉 query 即公网可访问 URL
  return presign.putUrl.split("?")[0]
}

