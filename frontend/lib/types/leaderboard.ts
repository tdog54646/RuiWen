export type RankType = "EXACT" | "ESTIMATE" | "UNRANKED"

export type LeaderboardTopItem = {
  rank: number
  userId: number
  score: number
  nickname: string | null
  avatar: string | null
}

export type LeaderboardTopResponse = {
  leaderboardType: string
  date: string
  offset: number
  limit: number
  items: LeaderboardTopItem[]
  hasMore: boolean
}

export type LeaderboardUserPosition = {
  leaderboardType: string
  date: string
  userId: number
  score: number
  rank: number | null
  rankType: RankType
  nickname: string | null
  avatar: string | null
}
