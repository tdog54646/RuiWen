"use client"

import { CornerRightUp, Mic, Square } from "lucide-react"
import { useState } from "react"
import { cn } from "@/lib/utils"
import { Textarea } from "@/components/ui/textarea"
import { useAutoResizeTextarea } from "@/components/hooks/use-auto-resize-textarea"

interface AIInputProps {
  id?: string
  placeholder?: string
  minHeight?: number
  maxHeight?: number
  onSubmit?: (value: string) => void
  /** 流式生成中：显示"停止"按钮替代"发送" */
  onStop?: () => void
  isStreaming?: boolean
  disabled?: boolean
  className?: string
}

/**
 * AI 输入框：自适应高度的胶囊式输入，Enter 发送 / Shift+Enter 换行。
 * 流式生成中（isStreaming）右侧按钮切换为"停止"。
 * 麦克风按钮为 UI 占位，语音输入功能后续实现。
 */
export function AIInput({
  id = "ai-input",
  placeholder = "Type your message...",
  minHeight = 52,
  maxHeight = 200,
  onSubmit,
  onStop,
  isStreaming = false,
  disabled = false,
  className,
}: AIInputProps) {
  const { textareaRef, adjustHeight } = useAutoResizeTextarea({
    minHeight,
    maxHeight,
  })
  const [inputValue, setInputValue] = useState("")

  const handleSubmit = () => {
    if (isStreaming || disabled) return
    if (!inputValue.trim()) return
    onSubmit?.(inputValue)
    setInputValue("")
    adjustHeight(true)
  }

  // 有输入内容或正在生成时，展示右侧动作按钮（麦克风随之左移避让）
  const showAction = inputValue.trim().length > 0 || isStreaming

  return (
    <div className={cn("w-full py-4", className)}>
      <div className="relative mx-auto w-full max-w-xl">
        <Textarea
          id={id}
          placeholder={placeholder}
          className={cn(
            "max-w-xl rounded-3xl px-4 py-4 pr-16",
            "bg-black/5 dark:bg-white/5",
            "text-wrap text-black dark:text-white",
            "placeholder:text-black/50 dark:placeholder:text-white/50",
            "border-none ring-black/20 dark:ring-white/20",
            "resize-none overflow-y-auto",
            "focus-visible:ring-0 focus-visible:ring-offset-0",
            "transition-[height] duration-100 ease-out",
            "[&::-webkit-resizer]:hidden",
          )}
          // 动态 min/max 高度用 inline style：Tailwind JIT 无法生成拼接类名 `min-h-[${n}px]`
          style={{ minHeight: `${minHeight}px`, maxHeight: `${maxHeight}px` }}
          ref={textareaRef}
          value={inputValue}
          onChange={(e) => {
            setInputValue(e.target.value)
            adjustHeight()
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault()
              handleSubmit()
            }
          }}
        />

        {/* 麦克风按钮：UI 占位，语音输入功能后续实现 */}
        <div
          title="语音输入（即将支持）"
          aria-hidden
          className={cn(
            "absolute top-1/2 -translate-y-1/2 rounded-xl bg-black/5 px-1 py-1 transition-all duration-200 dark:bg-white/5",
            showAction ? "right-10" : "right-3",
          )}
        >
          <Mic className="size-4 text-black/70 dark:text-white/70" />
        </div>

        {/* 发送 / 停止 按钮 */}
        <button
          type="button"
          onClick={isStreaming ? onStop : handleSubmit}
          disabled={disabled || (!isStreaming && !inputValue.trim())}
          aria-label={isStreaming ? "停止生成" : "发送"}
          className={cn(
            "absolute right-3 top-1/2 -translate-y-1/2 rounded-xl bg-black/5 px-1 py-1 transition-all duration-200 dark:bg-white/5",
            showAction
              ? "scale-100 opacity-100"
              : "pointer-events-none scale-95 opacity-0",
          )}
        >
          {isStreaming ? (
            <Square className="size-4 text-black/70 dark:text-white/70" />
          ) : (
            <CornerRightUp className="size-4 text-black/70 dark:text-white/70" />
          )}
        </button>
      </div>
    </div>
  )
}
