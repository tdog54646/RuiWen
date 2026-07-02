import { apiFetch } from "./client"
import type {
  LeaderboardTopResponse,
  LeaderboardUserPosition,
} from "@/lib/types/leaderboard"

const LEADERBOARD_PREFIX = "/api/leaderboards"

type ApiEnvelope<T> = {
  code: number
  message: string
  requestId: string
  data: T
}

function unwrapApiResponse<T>(resp: ApiEnvelope<T>): T {
  if (resp.code !== 0) {
    throw new Error(resp.message || "排行榜请求失败")
  }
  return resp.data
}

export const leaderboardService = {
  async top(params: {
    leaderboardType: string
    date: string
    offset?: number
    limit?: number
  }) {
    const { leaderboardType, date, offset = 0, limit = 20 } = params
    const usp = new URLSearchParams()
    usp.set("leaderboardType", leaderboardType)
    usp.set("date", date)
    usp.set("offset", String(offset))
    usp.set("limit", String(limit))

    const resp = await apiFetch<ApiEnvelope<LeaderboardTopResponse>>(
      `${LEADERBOARD_PREFIX}/top?${usp.toString()}`,
    )
    return unwrapApiResponse(resp)
  },

  async userPosition(params: {
    leaderboardType: string
    date: string
  }) {
    const { leaderboardType, date } = params
    const usp = new URLSearchParams()
    usp.set("leaderboardType", leaderboardType)
    usp.set("date", date)

    const resp = await apiFetch<ApiEnvelope<LeaderboardUserPosition>>(
      `${LEADERBOARD_PREFIX}/users/position?${usp.toString()}`,
    )
    return unwrapApiResponse(resp)
  },
}
