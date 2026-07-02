"use client"

import { useState, type KeyboardEvent, type ChangeEvent } from "react"
import { X } from "lucide-react"
import { cn } from "@/lib/utils"

type TagInputProps = {
  id?: string
  value: string[]
  onChange: (tags: string[]) => void
  placeholder?: string
  className?: string
}

export function TagInput({
  id,
  value,
  onChange,
  placeholder = "输入标签后按回车",
  className,
}: TagInputProps) {
  const [text, setText] = useState("")

  const addTag = (raw: string) => {
    const t = raw.trim()
    if (!t || value.includes(t)) {
      setText("")
      return
    }
    onChange([...value, t])
    setText("")
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      e.preventDefault()
      addTag(text)
    } else if (e.key === "Backspace" && text === "" && value.length > 0) {
      onChange(value.slice(0, -1))
    }
  }

  return (
    <div className={cn("flex flex-col gap-2", className)}>
      <input
        id={id}
        className="h-10 rounded-lg border bg-background px-3 text-sm outline-none focus:border-ring"
        value={text}
        onChange={(e: ChangeEvent<HTMLInputElement>) => setText(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
      />
      {value.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {value.map((tag) => (
            <span
              key={tag}
              className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary"
            >
              {tag}
              <button
                type="button"
                className="text-primary/60 hover:text-primary"
                onClick={() => onChange(value.filter((x) => x !== tag))}
              >
                <X className="size-3" />
              </button>
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
