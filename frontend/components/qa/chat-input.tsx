"use client"

import { AIInput } from "@/components/ui/ai-input"

/**
 * QA 专用输入框：对通用 AIInput 的薄包装，接入多轮问答的发送 / 停止逻辑。
 * 输入框样式与自适应高度由 AIInput 提供；这里只负责传参与底部容器外观。
 * onUploadImage 由父层提供（上传到 OSS），AIInput 内部展示图片按钮与预览。
 */
export function ChatInput({
  onSend,
  onStop,
  isStreaming,
  disabled,
  onUploadImage,
}: {
  onSend: (text: string, imageUrls?: string[]) => void
  onStop: () => void
  isStreaming: boolean
  disabled?: boolean
  onUploadImage?: (file: File) => Promise<string>
}) {
  return (
    <AIInput
      placeholder="输入问题，Enter 发送 / Shift+Enter 换行"
      onSubmit={onSend}
      onStop={onStop}
      isStreaming={isStreaming}
      disabled={disabled}
      minHeight={52}
      maxHeight={200}
      onUploadImage={onUploadImage}
      className="border-t border-white/50 bg-white/40 py-3 backdrop-blur-md"
    />
  )
}
