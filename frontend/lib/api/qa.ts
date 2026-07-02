import { apiFetchResponse } from "./client"

const RAG_PREFIX = "/api/rag"

export type QaStreamRequest = {
  question: string
  topK?: number
  maxTokens?: number
  accessToken?: string | null
  signal?: AbortSignal
  onMessage: (message: string) => void
}

function emitSseEvent(rawEvent: string, onMessage: (message: string) => void) {
  const dataLines = rawEvent
    .split(/\r?\n/)
    .filter((line) => line.startsWith("data:"))
    .map((line) => {
      const value = line.slice("data:".length).trim()
      return value.startsWith(" ") ? value.slice(1) : value
    })

  for (const line of dataLines) {
    if (!line || line === "[DONE]") continue
    try {
      const parsed = JSON.parse(line) as { content?: string; done?: boolean }
      if (parsed.content) {
        onMessage(parsed.content)
      }
    } catch {
      // non-JSON line, ignore
    }
  }
}

function consumeSseBuffer(
  buffer: string,
  onMessage: (message: string) => void,
) {
  let remaining = buffer
  while (remaining.length > 0) {
    const match = remaining.match(/\r?\n\r?\n/)
    if (!match || match.index === undefined) break

    const rawEvent = remaining.slice(0, match.index)
    emitSseEvent(rawEvent, onMessage)
    remaining = remaining.slice(match.index + match[0].length)
  }
  return remaining
}

async function readSseResponse(
  response: Response,
  onMessage: (message: string) => void,
) {
  if (!response.body) {
    emitSseEvent(await response.text(), onMessage)
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
      buffer = consumeSseBuffer(buffer, onMessage)
    }
  } finally {
    reader.releaseLock()
  }

  buffer += decoder.decode()
  if (buffer.trim()) {
    emitSseEvent(buffer, onMessage)
  }
}

async function streamQa(path: string, request: QaStreamRequest) {
  const response = await apiFetchResponse(path, {
    accessToken: request.accessToken,
    method: "POST",
    headers: {
      Accept: "text/event-stream, application/json",
      "Content-Type": "application/json",
    },
    body: {
      question: request.question.trim(),
      topK: request.topK ?? 5,
      maxTokens: request.maxTokens ?? 500,
    },
    signal: request.signal,
  })

  await readSseResponse(response, request.onMessage)
}

export const qaService = {
  streamKnowledgeBase: (request: QaStreamRequest) =>
    streamQa(`${RAG_PREFIX}/query`, request),
  streamKnowpost: (knowpostId: string, request: QaStreamRequest) =>
    streamQa(`${RAG_PREFIX}/knowpost/${knowpostId}`, request),
}
