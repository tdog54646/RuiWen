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
    <div className="glass-surface glass-border relative flex w-full items-center gap-2 rounded-full px-4 py-2.5">
      <Search className="size-5 text-violet-500" />
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
        <div className="glass-surface absolute left-0 top-full z-20 mt-2 w-full rounded-xl border border-white/60 p-1">
          {suggestLoading ? (
            <div className="px-3 py-2 text-sm text-slate-400">
              加载中…
            </div>
          ) : suggestions?.length ? (
            suggestions.map((s) => (
              <div
                key={s}
                className="cursor-pointer rounded-lg px-3 py-2 text-sm text-slate-700 transition-colors hover:bg-white/70"
                onMouseDown={() => onSuggestionClick?.(s)}
              >
                {s}
              </div>
            ))
          ) : (
            <div className="px-3 py-2 text-sm text-slate-400">
              无联想结果
            </div>
          )}
        </div>
      )}
    </div>
  )
}
