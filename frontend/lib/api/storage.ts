import { apiFetch } from "./client"
import type { PresignRequest, PresignResponse } from "@/lib/types/knowpost"

export const storageService = {
  presign: (payload: PresignRequest) =>
    apiFetch<PresignResponse>("/api/storage/presign", {
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
