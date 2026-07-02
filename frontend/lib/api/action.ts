import { apiFetch } from "./client"
import type {
  LikeActionResponse,
  FavActionResponse,
  CounterResponse,
} from "@/lib/types/knowpost"

export const actionService = {
  like: (entityId: string, accessToken: string, entityType = "knowpost") =>
    apiFetch<LikeActionResponse>("/api/action/like", {
      method: "POST",
      body: { entityType, entityId },
      accessToken,
    }),

  unlike: (entityId: string, accessToken: string, entityType = "knowpost") =>
    apiFetch<LikeActionResponse>("/api/action/unlike", {
      method: "POST",
      body: { entityType, entityId },
      accessToken,
    }),

  fav: (entityId: string, accessToken: string, entityType = "knowpost") =>
    apiFetch<FavActionResponse>("/api/action/fav", {
      method: "POST",
      body: { entityType, entityId },
      accessToken,
    }),

  unfav: (entityId: string, accessToken: string, entityType = "knowpost") =>
    apiFetch<FavActionResponse>("/api/action/unfav", {
      method: "POST",
      body: { entityType, entityId },
      accessToken,
    }),

  counters: (entityId: string, accessToken: string, entityType = "knowpost") =>
    apiFetch<CounterResponse>(
      `/api/counter/${entityType}/${entityId}?metrics=like,fav`,
      { accessToken },
    ),
}
