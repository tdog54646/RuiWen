// 通用 SSE（Server-Sent Events）流式解析。
// 仅负责按事件边界（空行）切分、合并同一事件的多行 data: 字段，并交给 onEvent。
// 上层自行把 data（字符串）解析为业务事件（如 JSON.parse）。

/**
 * 读取 SSE 响应流，对每个完整事件调用 onEvent(data)。
 * @param response fetch 原始 Response（需有 body 可读）
 * @param onEvent  收到单个事件的回调，data 为该事件所有 data: 行以 \n 拼接后的值
 */
export async function parseSseStream(
  response: Response,
  onEvent: (data: string) => void,
): Promise<void> {
  if (!response.body) {
    // 兜底：无流式 body 时整体当作一个事件
    const text = await response.text()
    if (text.trim()) {
      emitSseEvent(text, onEvent)
    }
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ""

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = consumeSseBuffer(buffer, onEvent)
    }
  } finally {
    reader.releaseLock()
  }

  // flush 尾部残留
  buffer += decoder.decode()
  if (buffer.trim()) {
    emitSseEvent(buffer, onEvent)
  }
}

function consumeSseBuffer(
  buffer: string,
  onEvent: (data: string) => void,
): string {
  let remaining = buffer
  while (remaining.length > 0) {
    const match = remaining.match(/\r?\n\r?\n/)
    if (!match || match.index === undefined) break

    const rawEvent = remaining.slice(0, match.index)
    emitSseEvent(rawEvent, onEvent)
    remaining = remaining.slice(match.index + match[0].length)
  }
  return remaining
}

function emitSseEvent(rawEvent: string, onEvent: (data: string) => void) {
  const dataLines = rawEvent
    .split(/\r?\n/)
    .filter((line) => line.startsWith("data:"))
    .map((line) => {
      const value = line.slice("data:".length)
      // SSE 规范：data: 后可有可选空格
      return value.startsWith(" ") ? value.slice(1) : value
    })

  if (dataLines.length === 0) return
  const data = dataLines.join("\n")
  if (!data || data === "[DONE]") return
  onEvent(data)
}
