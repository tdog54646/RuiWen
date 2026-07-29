"use client"

import { CornerRightUp, ImagePlus, Loader2, Mic, Square, X } from "lucide-react"
import { useRef, useState } from "react"
import { toast } from "sonner"
import { cn } from "@/lib/utils"
import { Textarea } from "@/components/ui/textarea"
import { useAutoResizeTextarea } from "@/components/hooks/use-auto-resize-textarea"
import { useVoiceInput } from "@/components/hooks/use-voice-input"

interface AIInputProps {
  id?: string
  placeholder?: string
  minHeight?: number
  maxHeight?: number
  onSubmit?: (value: string, imageUrls?: string[]) => void
  /** 流式生成中：显示"停止"按钮替代"发送" */
  onStop?: () => void
  isStreaming?: boolean
  disabled?: boolean
  className?: string
  /** 图片上传：父层负责上传到 OSS 并回传公网 URL；不传则不显示图片按钮。 */
  onUploadImage?: (file: File) => Promise<string>
}

/** 单条消息最多附带图片数（与后端 / QVQ 成本约束一致）。 */
const MAX_IMAGES = 4

/**
 * AI 输入框：自适应高度输入，Enter 发送 / Shift+Enter 换行。
 * 流式生成中（isStreaming）右侧按钮切换为"停止"。
 * 麦克风按钮：点击录音 → 再点停止 → 调用 ASR → 识别文本追加到输入框。
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
  onUploadImage,
}: AIInputProps) {
  const { textareaRef, adjustHeight } = useAutoResizeTextarea({
    minHeight,
    maxHeight,
  })
  const [inputValue, setInputValue] = useState("")
  // 录音起始时捕获已有文本，识别文本追加其后（不覆盖用户输入）
  const baseRef = useRef("")

  // 图片附件：onUploadImage 由父层完成 OSS 上传并回传公网 URL
  const [attachedImageUrls, setAttachedImageUrls] = useState<string[]>([])
  const [imageUploading, setImageUploading] = useState(false)
  const imageInputRef = useRef<HTMLInputElement | null>(null)
  const hasImage = Boolean(onUploadImage)

  const { status: voiceStatus, start: startVoice, stop: stopVoice } = useVoiceInput({
    onText: (text) => {
      setInputValue(baseRef.current + text)
      adjustHeight()
    },
    onError: (msg) => toast.error(msg),
  })

  const handleSelectImage = async (file: File | null | undefined) => {
    if (!file || !onUploadImage) return
    if (attachedImageUrls.length >= MAX_IMAGES) {
      toast.error(`最多附 ${MAX_IMAGES} 张图片`)
      return
    }
    setImageUploading(true)
    try {
      const url = await onUploadImage(file)
      setAttachedImageUrls((prev) => [...prev, url])
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "图片上传失败")
    } finally {
      setImageUploading(false)
      if (imageInputRef.current) imageInputRef.current.value = ""
    }
  }

  const handleSubmit = () => {
    if (isStreaming || disabled) return
    const text = inputValue.trim()
    if (!text && attachedImageUrls.length === 0) return
    onSubmit?.(inputValue, attachedImageUrls.length > 0 ? attachedImageUrls : undefined)
    setInputValue("")
    setAttachedImageUrls([])
    adjustHeight(true)
  }

  // 有输入内容或正在生成时，展示发送/停止按钮；录音中隐藏发送按钮避免误触
  const voiceActive = voiceStatus === "connecting" || voiceStatus === "recording" || voiceStatus === "stopping"
  const showAction = !voiceActive && (inputValue.trim().length > 0 || attachedImageUrls.length > 0 || isStreaming)

  return (
    <div className={cn("w-full py-4", className)}>
      <div className="mx-auto w-full max-w-xl">
        {/* 已附图片预览：用户右侧对齐，逐张 X 移除 */}
        {attachedImageUrls.length > 0 && (
          <div className="mb-2 flex flex-wrap justify-end gap-2">
            {attachedImageUrls.map((url, idx) => (
              <div key={url + idx} className="group relative">
                <img
                  src={url}
                  alt="附件"
                  className="size-16 rounded-lg object-cover ring-1 ring-black/10"
                />
                <button
                  type="button"
                  onClick={() => setAttachedImageUrls((prev) => prev.filter((_, i) => i !== idx))}
                  aria-label="移除图片"
                  title="移除图片"
                  className="absolute -right-1.5 -top-1.5 flex size-5 items-center justify-center rounded-full bg-black/55 text-white backdrop-blur transition-opacity hover:bg-black/70"
                >
                  <X className="size-3" />
                </button>
              </div>
            ))}
          </div>
        )}

        <div className="relative w-full">
        <Textarea
          id={id}
          placeholder={placeholder}
          className={cn(
            "max-w-xl rounded-lg border border-[#cfd1ca] py-4 pr-16",
            hasImage ? "pl-10" : "pl-4",
            "bg-[#f3f3ee] dark:bg-white/5",
            "text-wrap text-[#252a27] dark:text-white",
            "placeholder:text-[#8d918c] dark:placeholder:text-white/50",
            "ring-[#2f5d50]/15 dark:ring-white/20",
            "resize-none overflow-y-auto",
            "focus-visible:border-[#2f5d50] focus-visible:ring-2 focus-visible:ring-[#2f5d50]/10 focus-visible:ring-offset-0",
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
            // 输入法组词中的回车用于上屏，不触发发送（isComposing 为标准信号；
            // keyCode 229 兼容老 Safari 等未及时翻转 isComposing 的浏览器）
            if (e.nativeEvent.isComposing || e.keyCode === 229) return
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault()
              handleSubmit()
            }
          }}
        />

        {/* 图片上传按钮：点击触发文件选择；上传中转圈 */}
        {hasImage && (
          <button
            type="button"
            onClick={() => imageInputRef.current?.click()}
            disabled={imageUploading || isStreaming || voiceActive}
            aria-label="上传图片"
            title="上传图片"
            className={cn(
              "absolute top-1/2 -translate-y-1/2 rounded-xl px-1 py-1 transition-all duration-200 disabled:opacity-40",
              "left-3 bg-black/5 dark:bg-white/5",
            )}
          >
            {imageUploading ? (
              <Loader2 className="size-4 animate-spin text-black/70 dark:text-white/70" />
            ) : (
              <ImagePlus className="size-4 text-black/70 dark:text-white/70" />
            )}
          </button>
        )}
        <input
          ref={imageInputRef}
          type="file"
          accept="image/*"
          className="hidden"
          onChange={(e) => handleSelectImage(e.target.files?.[0])}
        />

        {/* 麦克风按钮：点击录音 → 实时流式识别文字逐字出现 → 再点停止。
            idle 时追加已有文本到 baseRef；recording 红脉冲；stopping 转圈 */}
        <button
          type="button"
          onClick={() => {
            if (voiceStatus === "idle") {
              baseRef.current = inputValue
              startVoice()
            } else if (voiceStatus === "recording") {
              stopVoice()
            }
          }}
          disabled={voiceStatus === "connecting" || voiceStatus === "stopping" || isStreaming}
          aria-label={
            voiceStatus === "recording" || voiceStatus === "connecting"
              ? "停止录音"
              : "语音输入"
          }
          title={
            voiceStatus === "connecting" || voiceStatus === "recording"
              ? "录音中，点击停止"
              : voiceStatus === "stopping"
                ? "正在结束…"
                : "语音输入"
          }
          className={cn(
            "absolute top-1/2 -translate-y-1/2 rounded-xl px-1 py-1 transition-all duration-200 disabled:opacity-40",
            "right-10",
            voiceStatus === "connecting" || voiceStatus === "recording"
              ? "animate-pulse bg-red-500/15"
              : "bg-black/5 dark:bg-white/5",
          )}
        >
          {voiceStatus === "stopping" ? (
            <Loader2 className="size-4 animate-spin text-black/70 dark:text-white/70" />
          ) : (
            <Mic
              className={cn(
                "size-4 text-black/70 dark:text-white/70",
                (voiceStatus === "connecting" || voiceStatus === "recording") && "text-red-500",
              )}
            />
          )}
        </button>

        {/* 发送 / 停止 按钮 */}
        <button
          type="button"
          onClick={isStreaming ? onStop : handleSubmit}
          disabled={disabled || (!isStreaming && !inputValue.trim() && attachedImageUrls.length === 0)}
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
    </div>
  )
}
