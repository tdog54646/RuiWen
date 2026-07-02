"use client"

import { useState } from "react"
import { Eye, Edit3 } from "lucide-react"

import { Button } from "@/components/ui/button"
import { MarkdownRenderer } from "@/components/ui/markdown-renderer"
import { cn } from "@/lib/utils"

type MarkdownEditorProps = {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  className?: string
  minHeight?: string
}

export function MarkdownEditor({
  value,
  onChange,
  placeholder = "使用 Markdown 编写内容...",
  className,
  minHeight = "400px",
}: MarkdownEditorProps) {
  const [mode, setMode] = useState<"edit" | "preview" | "split">("split")

  return (
    <div className={cn("flex flex-col rounded-lg border", className)}>
      <div className="flex items-center gap-1 border-b px-2 py-1.5">
        <Button
          variant={mode === "edit" ? "secondary" : "ghost"}
          size="xs"
          onClick={() => setMode("edit")}
        >
          <Edit3 className="mr-1 size-3" />
          编辑
        </Button>
        <Button
          variant={mode === "split" ? "secondary" : "ghost"}
          size="xs"
          onClick={() => setMode("split")}
        >
          分栏
        </Button>
        <Button
          variant={mode === "preview" ? "secondary" : "ghost"}
          size="xs"
          onClick={() => setMode("preview")}
        >
          <Eye className="mr-1 size-3" />
          预览
        </Button>
      </div>

      <div className="flex flex-1" style={{ minHeight }}>
        {mode !== "preview" && (
          <textarea
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder}
            className={cn(
              "flex-1 resize-none bg-transparent p-4 text-sm leading-relaxed outline-none placeholder:text-muted-foreground",
              mode === "split" && "border-r"
            )}
          />
        )}
        {mode !== "edit" && (
          <div className="flex-1 overflow-auto p-4">
            {value ? (
              <MarkdownRenderer content={value} />
            ) : (
              <p className="text-sm text-muted-foreground">预览区域</p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
