import { apiFetch } from "./client"
import type { SearchResponse, SuggestResponse } from "@/lib/types/search"

const SEARCH_PREFIX = "/api/search"

export const searchService = {
  query: (params: {
    q: string
    size?: number
    tags?: string
    after?: string | null
  }) => {
    const { q, size = 20, tags, after } = params
    const usp = new URLSearchParams()
    usp.set("q", q)
    if (size) usp.set("size", String(size))
    if (tags) usp.set("tags", tags)
    if (after) usp.set("after", after)
    return apiFetch<SearchResponse>(`${SEARCH_PREFIX}?${usp.toString()}`)
  },

  suggest: (prefix: string, size = 10) => {
    const usp = new URLSearchParams()
    usp.set("prefix", prefix)
    if (size) usp.set("size", String(size))
    return apiFetch<SuggestResponse>(
      `${SEARCH_PREFIX}/suggest?${usp.toString()}`,
    )
  },
}
