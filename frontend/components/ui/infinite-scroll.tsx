"use client"

import { useEffect, useRef } from "react"

type InfiniteScrollProps = {
  onLoadMore: () => void
  hasMore: boolean
  loading: boolean
  rootMargin?: string
  children?: React.ReactNode
}

export function InfiniteScroll({
  onLoadMore,
  hasMore,
  loading,
  rootMargin = "200px",
  children,
}: InfiniteScrollProps) {
  const sentinelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = sentinelRef.current
    if (!el || !hasMore) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !loading) {
          onLoadMore()
        }
      },
      { rootMargin }
    )

    observer.observe(el)
    return () => observer.disconnect()
  }, [onLoadMore, hasMore, loading, rootMargin])

  return (
    <>
      {children}
      {hasMore && <div ref={sentinelRef} className="h-px" />}
    </>
  )
}
