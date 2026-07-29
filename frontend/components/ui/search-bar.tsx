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
    <div className="relative flex w-full items-center gap-3 rounded-xl bg-[#fbfbf8] p-2 pl-4 shadow-[0_14px_35px_-28px_rgba(29,33,31,0.55)] ring-1 ring-[#d8d9d2] transition-shadow focus-within:ring-2 focus-within:ring-[#2f5d50]/40">
      <Search className="size-5 text-[#2f5d50]" strokeWidth={1.7} />
      <input
        className="min-w-0 flex-1 bg-transparent text-[15px] text-[#252a27] outline-none placeholder:text-[#969994]"
        value={value}
        placeholder={placeholder}
        onChange={(e: ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setTimeout(() => setFocused(false), 120)}
        onKeyDown={(e) => {
          if (e.key === "Enter") onSubmit?.()
        }}
      />
      <Button size="sm" className="h-10 rounded-lg bg-[#1d211f] px-6 hover:bg-[#2f5d50]" onClick={onSubmit}>
        {buttonLabel}
      </Button>

      {focused && (value?.trim()?.length ?? 0) > 0 && (
        <div className="absolute left-0 top-full z-20 mt-2 w-full rounded-lg border border-[#d8d9d2] bg-[#fbfbf8] p-1.5 shadow-[0_20px_50px_-32px_rgba(29,33,31,0.55)]">
          {suggestLoading ? (
            <div className="px-3 py-2 text-sm text-slate-400">
              加载中…
            </div>
          ) : suggestions?.length ? (
            suggestions.map((s) => (
              <div
                key={s}
                className="cursor-pointer rounded-md px-3 py-2 text-sm text-[#555a56] transition-colors hover:bg-[#ecece6] hover:text-[#1d211f]"
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
