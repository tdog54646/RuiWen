"use client"

import { AIInput } from "@/components/ui/ai-input"

/**
 * QA 专用输入框：对通用 AIInput 的薄包装，接入多轮问答的发送 / 停止逻辑。
 * 输入框样式与自适应高度由 AIInput 提供；这里只负责传参与底部容器外观。
 */
export function ChatInput({
  onSend,
  onStop,
  isStreaming,
  disabled,
}: {
  onSend: (text: string) => void
  onStop: () => void
  isStreaming: boolean
  disabled?: boolean
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
      className="border-t border-white/50 bg-white/40 py-3 backdrop-blur-md"
    />
  )
}
