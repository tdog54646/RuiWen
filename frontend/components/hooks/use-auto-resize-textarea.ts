import { useEffect, useRef, useCallback } from "react"

interface UseAutoResizeTextareaProps {
  minHeight: number
  maxHeight?: number
}

/**
 * 文本框自适应高度 hook：根据内容动态调整高度，限制在 [minHeight, maxHeight] 区间。
 * 提供 reset 参数用于清空后重置到初始高度。
 */
export function useAutoResizeTextarea({
  minHeight,
  maxHeight,
}: UseAutoResizeTextareaProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const adjustHeight = useCallback(
    (reset?: boolean) => {
      const textarea = textareaRef.current
      if (!textarea) return

      if (reset) {
        textarea.style.height = `${minHeight}px`
        return
      }

      // 先收缩到最小高度，再依据 scrollHeight 重新撑开，得到准确的目标高度
      textarea.style.height = `${minHeight}px`

      const newHeight = Math.max(
        minHeight,
        Math.min(textarea.scrollHeight, maxHeight ?? Number.POSITIVE_INFINITY),
      )

      textarea.style.height = `${newHeight}px`
    },
    [minHeight, maxHeight],
  )

  useEffect(() => {
    const textarea = textareaRef.current
    if (textarea) {
      textarea.style.height = `${minHeight}px`
    }
  }, [minHeight])

  // 窗口尺寸变化时重新计算（换行宽度改变会影响 scrollHeight）
  useEffect(() => {
    const handleResize = () => adjustHeight()
    window.addEventListener("resize", handleResize)
    return () => window.removeEventListener("resize", handleResize)
  }, [adjustHeight])

  return { textareaRef, adjustHeight }
}
