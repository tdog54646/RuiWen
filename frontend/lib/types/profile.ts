import type { Gender } from "./auth"

export type ProfileUpdateRequest = {
  nickname?: string
  bio?: string
  lineId?: string
  gender?: Gender
  birthday?: string
  school?: string
  email?: string
  phone?: string
  tagJson?: string
}

export type ProfileResponse = {
  id: number
  nickname: string
  avatar: string
  bio?: string
  lineId?: string
  gender?: Gender
  birthday?: string
  school?: string
  email?: string
  phone?: string
  tagJson?: string
}
