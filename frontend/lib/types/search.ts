import type { FeedItem } from "./knowpost"

export type SearchResponse = {
  items: FeedItem[]
  nextAfter: string | null
  hasMore: boolean
}

export type SuggestResponse = {
  items: string[]
}
