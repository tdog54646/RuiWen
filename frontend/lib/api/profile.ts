import { apiFetch } from "./client"
import type { ProfileResponse, ProfileUpdateRequest } from "@/lib/types/profile"

const PROFILE_PREFIX = "/api/profile"

export const profileService = {
  get: (accessToken?: string) =>
    apiFetch<ProfileResponse>(`${PROFILE_PREFIX}`, {
      accessToken: accessToken ?? undefined,
    }),

  getByUserId: (userId: number) =>
    apiFetch<ProfileResponse>(`${PROFILE_PREFIX}/${userId}`),

  update: (payload: ProfileUpdateRequest) =>
    apiFetch<ProfileResponse>(`${PROFILE_PREFIX}`, {
      method: "PATCH",
      body: payload,
    }),

  uploadAvatar: (file: File) => {
    const form = new FormData()
    form.append("file", file)
    return apiFetch<ProfileResponse>(`${PROFILE_PREFIX}/avatar`, {
      method: "POST",
      body: form,
    })
  },
}
