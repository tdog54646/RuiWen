"use client"

import { useState, type ChangeEvent } from "react"
import { Search } from "lucide-react"
import { Button } from "@/components/ui/button"

type SearchBarProps = {
  placeholder?: string
  value: string
  onChange: (value: string) => void
  onSubmit?: () => void
  buttonLabel?: string
  suggestions?: string[]
  suggestLoading?: boolean
  onSuggestionClick?: (value: string) => void
}

export function SearchBar({
  placeholder,
  value,
  onChange,
  onSubmit,
  buttonLabel = "搜索",
  suggestions = [],
  suggestLoading = false,
  onSuggestionClick,
}: SearchBarProps) {
  const [focused, setFocused] = useState(false)

  return (
    <div className="relative flex w-full items-center gap-2 rounded-full border bg-background/80 px-4 py-2 shadow-sm">
      <Search className="size-5 text-muted-foreground" />
      <input
        className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
        value={value}
        placeholder={placeholder}
        onChange={(e: ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setTimeout(() => setFocused(false), 120)}
        onKeyDown={(e) => {
          if (e.key === "Enter") onSubmit?.()
        }}
      />
      <Button size="sm" className="rounded-full" onClick={onSubmit}>
        {buttonLabel}
      </Button>

      {focused && (value?.trim()?.length ?? 0) > 0 && (
        <div className="absolute left-0 top-full z-10 mt-2 w-full rounded-xl border bg-background p-1 shadow-lg">
          {suggestLoading ? (
            <div className="px-3 py-2 text-sm text-muted-foreground">
              加载中…
            </div>
          ) : suggestions?.length ? (
            suggestions.map((s) => (
              <div
                key={s}
                className="cursor-pointer rounded-lg px-3 py-2 text-sm hover:bg-muted"
                onMouseDown={() => onSuggestionClick?.(s)}
              >
                {s}
              </div>
            ))
          ) : (
            <div className="px-3 py-2 text-sm text-muted-foreground">
              无联想结果
            </div>
          )}
        </div>
      )}
    </div>
  )
}
