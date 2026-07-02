import { apiFetch } from "./client"
import type {
  RelationStatusResponse,
  RelationCountersResponse,
} from "@/lib/types/relation"
import type { ProfileResponse } from "@/lib/types/profile"

const RELATION_PREFIX = "/api/relation"

export const relationService = {
  follow: (toUserId: number, accessToken: string) =>
    apiFetch<boolean>(
      `${RELATION_PREFIX}/follow?toUserId=${toUserId}`,
      { method: "POST", accessToken },
    ),

  unfollow: (toUserId: number, accessToken: string) =>
    apiFetch<boolean>(
      `${RELATION_PREFIX}/unfollow?toUserId=${toUserId}`,
      { method: "POST", accessToken },
    ),

  status: (toUserId: number, accessToken: string) =>
    apiFetch<RelationStatusResponse>(
      `${RELATION_PREFIX}/status?toUserId=${toUserId}`,
      { accessToken },
    ),

  following: (
    userId: number,
    limit = 20,
    offset = 0,
    cursor?: number,
    accessToken?: string,
  ) => {
    const params = new URLSearchParams({
      userId: String(userId),
      limit: String(limit),
      offset: String(offset),
    })
    if (typeof cursor === "number") params.set("cursor", String(cursor))
    return apiFetch<ProfileResponse[]>(
      `${RELATION_PREFIX}/following?${params.toString()}`,
      { accessToken: accessToken ?? null },
    )
  },

  followers: (
    userId: number,
    limit = 20,
    offset = 0,
    cursor?: number,
    accessToken?: string,
  ) => {
    const params = new URLSearchParams({
      userId: String(userId),
      limit: String(limit),
      offset: String(offset),
    })
    if (typeof cursor === "number") params.set("cursor", String(cursor))
    return apiFetch<ProfileResponse[]>(
      `${RELATION_PREFIX}/followers?${params.toString()}`,
      { accessToken: accessToken ?? null },
    )
  },

  counters: (userId: number, accessToken?: string | null) =>
    apiFetch<RelationCountersResponse>(
      `${RELATION_PREFIX}/counter?userId=${userId}`,
      { accessToken: accessToken ?? undefined },
    ),
}
