export type CreateDraftResponse = {
  id: string
}

export type PresignRequest = {
  scene: "knowpost_content" | "knowpost_image"
  postId: string
  contentType: string
  ext: string
}

export type PresignResponse = {
  objectKey: string
  putUrl: string
  headers: Record<string, string>
  expiresIn: number
}

export type ConfirmContentRequest = {
  objectKey: string
  etag: string
  size: number
  sha256: string
}

export type VisibleScope = "public" | "followers" | "school" | "private" | "unlisted"

export type UpdateKnowPostRequest = {
  title?: string
  tagId?: number
  tags?: string[]
  imgUrls?: string[]
  visible?: VisibleScope
  isTop?: boolean
  description?: string
}

export type EditKnowPostRequest = ConfirmContentRequest & UpdateKnowPostRequest & {
  title: string
}

export type FeedItem = {
  id: string
  title: string
  description: string
  coverImage?: string
  tags: string[]
  tagJson?: string
  authorId?: number
  authorAvatar?: string
  authorAvator?: string
  authorNickname: string
  likeCount?: number
  favoriteCount?: number
  liked?: boolean
  faved?: boolean
  isTop?: boolean
  visible?: VisibleScope
  publishTime?: string | number
}

export type FeedResponse = {
  items: FeedItem[]
  page: number
  size: number
  hasMore: boolean
}

export type KnowpostDetailResponse = {
  id: string
  title: string
  description: string
  contentUrl: string
  images: string[]
  tags: string[]
  authorAvatar?: string
  authorNickname: string
  authorId?: number
  authorTagJson?: string
  likeCount: number
  favoriteCount: number
  liked?: boolean
  faved?: boolean
  isTop: boolean
  visible: VisibleScope
  type: "image_text" | string
  publishTime?: string | number
}

export type LikeActionResponse = {
  changed: boolean
  liked: boolean
}

export type FavActionResponse = {
  changed: boolean
  faved: boolean
}

export type CounterResponse = {
  entityType: string
  entityId: string
  counts: {
    like: number
    fav: number
  }
}
