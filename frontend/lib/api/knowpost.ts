import { apiFetch } from "./client"
import type {
  CreateDraftResponse,
  PresignRequest,
  PresignResponse,
  ConfirmContentRequest,
  EditKnowPostRequest,
  UpdateKnowPostRequest,
  FeedResponse,
  KnowpostDetailResponse,
  LikeActionResponse,
  FavActionResponse,
  CounterResponse,
  KnowpostHotQuestionResponse,
  VisibleScope,
} from "@/lib/types/knowpost"

const KNOWPOST_PREFIX = "/api/knowposts"
const STORAGE_PREFIX = "/api/storage"

export const knowpostService = {
  createDraft: () =>
    apiFetch<CreateDraftResponse>(`${KNOWPOST_PREFIX}/drafts`, {
      method: "POST",
    }),

  presign: (payload: PresignRequest) =>
    apiFetch<PresignResponse>(`${STORAGE_PREFIX}/presign`, {
      method: "POST",
      body: payload,
    }),

  confirmContent: (id: string, payload: ConfirmContentRequest) =>
    apiFetch<void>(`${KNOWPOST_PREFIX}/${id}/content/confirm`, {
      method: "POST",
      body: payload,
    }),

  update: (id: string, payload: UpdateKnowPostRequest) =>
    apiFetch<void>(`${KNOWPOST_PREFIX}/${id}`, {
      method: "PATCH",
      body: payload,
    }),

  saveEdit: (id: string, payload: EditKnowPostRequest) =>
    apiFetch<void>(`${KNOWPOST_PREFIX}/${id}`, {
      method: "PUT",
      body: payload,
    }),

  publish: (id: string) =>
    apiFetch<void>(`${KNOWPOST_PREFIX}/${id}/publish`, { method: "POST" }),

  setTop: (id: string, isTop: boolean, accessToken: string) =>
    apiFetch<void>(`${KNOWPOST_PREFIX}/${id}/top`, {
      method: "PATCH",
      body: { isTop },
      accessToken,
    }),

  setVisibility: (id: string, visible: VisibleScope, accessToken: string) =>
    apiFetch<void>(`${KNOWPOST_PREFIX}/${id}/visibility`, {
      method: "PATCH",
      body: { visible },
      accessToken,
    }),

  remove: (id: string, accessToken: string) =>
    apiFetch<void>(`${KNOWPOST_PREFIX}/${id}`, {
      method: "DELETE",
      accessToken,
    }),

  feed: (page = 1, size = 20) =>
    apiFetch<FeedResponse>(
      `${KNOWPOST_PREFIX}/feed?page=${page}&size=${size}`,
    ),

  mine: (page = 1, size = 20, accessToken: string) =>
    apiFetch<FeedResponse>(
      `${KNOWPOST_PREFIX}/mine?page=${page}&size=${size}`,
      { accessToken },
    ),

  user: (userId: number, page = 1, size = 20, accessToken?: string | null) =>
    apiFetch<FeedResponse>(
      `${KNOWPOST_PREFIX}/user?page=${page}&size=${size}&userId=${userId}`,
      {
        accessToken: accessToken ?? undefined,
      },
    ),

  detail: (id: string, accessToken?: string) =>
    apiFetch<KnowpostDetailResponse>(`${KNOWPOST_PREFIX}/detail/${id}`, {
      accessToken: accessToken ?? null,
    }),

  hotQuestion: (id: string, limit = 10) =>
    apiFetch<KnowpostHotQuestionResponse>(
      `${KNOWPOST_PREFIX}/${id}/qa/hotquestion?limit=${limit}`,
    ),

  suggestDescription: (content: string, accessToken: string) =>
    apiFetch<{ description: string }>(
      `${KNOWPOST_PREFIX}/description/suggest`,
      {
        method: "POST",
        body: { content },
        accessToken,
      },
    ),

  like: (
    entityId: string,
    accessToken: string,
    entityType: string = "knowpost",
  ) =>
    apiFetch<LikeActionResponse>(`/api/action/like`, {
      method: "POST",
      body: { entityType, entityId },
      accessToken,
    }),

  unlike: (
    entityId: string,
    accessToken: string,
    entityType: string = "knowpost",
  ) =>
    apiFetch<LikeActionResponse>(`/api/action/unlike`, {
      method: "POST",
      body: { entityType, entityId },
      accessToken,
    }),

  fav: (
    entityId: string,
    accessToken: string,
    entityType: string = "knowpost",
  ) =>
    apiFetch<FavActionResponse>(`/api/action/fav`, {
      method: "POST",
      body: { entityType, entityId },
      accessToken,
    }),

  unfav: (
    entityId: string,
    accessToken: string,
    entityType: string = "knowpost",
  ) =>
    apiFetch<FavActionResponse>(`/api/action/unfav`, {
      method: "POST",
      body: { entityType, entityId },
      accessToken,
    }),

  counters: (
    entityId: string,
    accessToken: string,
    entityType: string = "knowpost",
  ) =>
    apiFetch<CounterResponse>(
      `/api/counter/${entityType}/${entityId}?metrics=like,fav`,
      { accessToken },
    ),
}

export function ensureHttps(url: string): string {
  if (
    typeof window !== "undefined" &&
    window.location.protocol === "https:" &&
    url.startsWith("http://")
  ) {
    return url.replace("http://", "https://")
  }
  return url
}

export function withCacheBuster(url: string): string {
  const normalized = ensureHttps(url)
  const separator = normalized.includes("?") ? "&" : "?"
  return `${normalized}${separator}_rwv=${Date.now()}`
}

export async function uploadToPresigned(
  putUrl: string,
  headers: Record<string, string>,
  file: File,
) {
  const resp = await fetch(ensureHttps(putUrl), {
    method: "PUT",
    headers,
    body: file,
    credentials: "omit",
  })
  if (!resp.ok) {
    const text = await resp.text().catch(() => "")
    throw new Error(text || `上传失败：${resp.status}`)
  }
  const etag = resp.headers.get("ETag") || resp.headers.get("etag") || ""
  return { etag }
}

export async function computeSha256(file: File) {
  const buf = await file.arrayBuffer()
  if (typeof crypto !== "undefined" && crypto.subtle) {
    const digest = await crypto.subtle.digest("SHA-256", buf)
    return Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("")
  }
  return sha256Fallback(new Uint8Array(buf))
}

/** Pure-JS SHA-256 for non-secure contexts (HTTP over LAN, etc.) */
function sha256Fallback(bytes: Uint8Array): string {
  const K = new Uint32Array([
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
  ])

  const rotr = (x: number, n: number) => (x >>> n) | (x << (32 - n))

  const msgLen = bytes.length
  const bitLen = msgLen * 8
  const totalLen = Math.ceil((msgLen + 9) / 64) * 64
  const padded = new Uint8Array(totalLen)
  padded.set(bytes)
  padded[msgLen] = 0x80
  const dv = new DataView(padded.buffer)
  dv.setUint32(totalLen - 8, Math.floor(bitLen / 0x100000000), false)
  dv.setUint32(totalLen - 4, bitLen >>> 0, false)

  let h0 = 0x6a09e667, h1 = 0xbb67ae85, h2 = 0x3c6ef372, h3 = 0xa54ff53a
  let h4 = 0x510e527f, h5 = 0x9b05688c, h6 = 0x1f83d9ab, h7 = 0x5be0cd19

  const W = new Uint32Array(64)

  for (let off = 0; off < totalLen; off += 64) {
    for (let i = 0; i < 16; i++) W[i] = dv.getUint32(off + i * 4, false)
    for (let i = 16; i < 64; i++) {
      const s0 = rotr(W[i - 15], 7) ^ rotr(W[i - 15], 18) ^ (W[i - 15] >>> 3)
      const s1 = rotr(W[i - 2], 17) ^ rotr(W[i - 2], 19) ^ (W[i - 2] >>> 10)
      W[i] = (W[i - 16] + s0 + W[i - 7] + s1) | 0
    }
    let a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, h = h7
    for (let i = 0; i < 64; i++) {
      const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)
      const ch = (e & f) ^ (~e & g)
      const t1 = (h + S1 + ch + K[i] + W[i]) | 0
      const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)
      const maj = (a & b) ^ (a & c) ^ (b & c)
      const t2 = (S0 + maj) | 0
      h = g; g = f; f = e; e = (d + t1) | 0
      d = c; c = b; b = a; a = (t1 + t2) | 0
    }
    h0 = (h0 + a) | 0; h1 = (h1 + b) | 0; h2 = (h2 + c) | 0; h3 = (h3 + d) | 0
    h4 = (h4 + e) | 0; h5 = (h5 + f) | 0; h6 = (h6 + g) | 0; h7 = (h7 + h) | 0
  }

  const hex = (n: number) => (n >>> 0).toString(16).padStart(8, "0")
  return hex(h0) + hex(h1) + hex(h2) + hex(h3) + hex(h4) + hex(h5) + hex(h6) + hex(h7)
}
