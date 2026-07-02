export type RelationStatusResponse = {
  following: boolean
  followedBy: boolean
  mutual: boolean
}

export type RelationCountersResponse = {
  followings: number
  followers: number
  posts: number
  likedPosts: number
  favedPosts: number
}
