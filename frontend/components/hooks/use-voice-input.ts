"use client"

import { useCallback, useEffect, useRef, useState } from "react"

export type VoiceInputStatus = "idle" | "connecting" | "recording" | "stopping"

interface UseVoiceInputOptions {
  /** 实时识别文本回调（后端累计值，含已结束句 + 当前句 partial）。 */
  onText: (text: string) => void
  /** 错误回调（权限被拒、识别失败等）。 */
  onError?: (message: string) => void
  /** 最大录音时长，到达后自动停止。默认 60s。 */
  maxDurationMs?: number
}

interface UseVoiceInputReturn {
  status: VoiceInputStatus
  start: () => void
  stop: () => void
}

/** 目标采样率：DashScope paraformer-realtime-v2 要求 16kHz PCM16。 */
const TARGET_SAMPLE_RATE = 16000
const ASR_PATH = "/ws/asr"

/**
 * 实时流式语音输入 hook。
 * <p>点击 start() → 连后端 WS（带 JWT query 参数）→ getUserMedia + AudioContext(16kHz)
 * → ScriptProcessorNode 采集 PCM → Int16 二进制帧实时推送给后端中继 → 后端转发 DashScope
 * 并回推 result-generated，本 hook 通过 onText 实时刷新文本。点击 stop() 发 {action:"stop"}，
 * 后端发 finish-task、收尾并关闭 WS。
 *
 * 防回声：经 gain=0 的静音节点把处理器接入 destination（仅图活跃、不回放声音）。
 */
export function useVoiceInput({
  onText,
  onError,
  maxDurationMs = 60_000,
}: UseVoiceInputOptions): UseVoiceInputReturn {
  const [status, setStatus] = useState<VoiceInputStatus>("idle")

  const wsRef = useRef<WebSocket | null>(null)
  const audioCtxRef = useRef<AudioContext | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const sourceRef = useRef<MediaStreamAudioSourceNode | null>(null)
  const processorRef = useRef<ScriptProcessorNode | null>(null)
  const muteRef = useRef<GainNode | null>(null)
  const recordingRef = useRef<boolean>(false)
  const stopFnRef = useRef<() => void>(() => {}) // 避免 openMic timer 捕获过时的 stop
  const maxTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const stopTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const onTextRef = useRef(onText)
  const onErrorRef = useRef(onError)
  useEffect(() => {
    onTextRef.current = onText
    onErrorRef.current = onError
  }, [onText, onError])

  // -- 释放麦克风 / AudioContext 资源（不动 WS） -------------------------
  const stopMic = useCallback(() => {
    recordingRef.current = false
    processorRef.current?.disconnect()
    muteRef.current?.disconnect()
    sourceRef.current?.disconnect()
    streamRef.current?.getTracks().forEach((t) => t.stop())
    processorRef.current = null
    muteRef.current = null
    sourceRef.current = null
    streamRef.current = null
    if (audioCtxRef.current) {
      audioCtxRef.current.close().catch(() => {})
      audioCtxRef.current = null
    }
  }, [])

  // -- 完全收尾：停 mic + 关 WS + 清计时器 --------------------------------
  const teardown = useCallback(() => {
    if (maxTimerRef.current) {
      clearTimeout(maxTimerRef.current)
      maxTimerRef.current = null
    }
    if (stopTimerRef.current) {
      clearTimeout(stopTimerRef.current)
      stopTimerRef.current = null
    }
    stopMic()
    const ws = wsRef.current
    if (ws) {
      ws.onopen = null
      ws.onmessage = null
      ws.onerror = null
      ws.onclose = null
      if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
        ws.close()
      }
      wsRef.current = null
    }
    setStatus("idle")
  }, [stopMic])

  const fail = useCallback(
    (message: string) => {
      teardown()
      onErrorRef.current?.(message)
    },
    [teardown],
  )

  // -- 打开麦克风并实时推送 PCM（必须在 start 之前声明） ------------------
  const openMic = useCallback(async (ws: WebSocket) => {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const AudioCtx: typeof AudioContext | undefined =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext })
        .webkitAudioContext
    if (!AudioCtx) {
      stream.getTracks().forEach((t) => t.stop())
      throw new Error("当前浏览器不支持语音录制")
    }

    const audioCtx = new AudioCtx({ sampleRate: TARGET_SAMPLE_RATE })
    const srcRate = audioCtx.sampleRate
    audioCtxRef.current = audioCtx
    streamRef.current = stream

    const source = audioCtx.createMediaStreamSource(stream)
    const processor = audioCtx.createScriptProcessor(2048, 1, 1)
    const mute = audioCtx.createGain()
    mute.gain.value = 0
    sourceRef.current = source
    processorRef.current = processor
    muteRef.current = mute

    processor.onaudioprocess = (e) => {
      if (!recordingRef.current) return
      if (ws.readyState !== WebSocket.OPEN) return
      const input = e.inputBuffer.getChannelData(0)
      const samples =
        srcRate === TARGET_SAMPLE_RATE ? input : resample(input, srcRate, TARGET_SAMPLE_RATE)
      ws.send(float32ToPcm16(samples))
    }
    source.connect(processor)
    processor.connect(mute)
    mute.connect(audioCtx.destination)

    recordingRef.current = true
    maxTimerRef.current = setTimeout(() => {
      stopFnRef.current()
    }, maxDurationMs)
  }, [maxDurationMs])

  // -- 启动 -------------------------------------------------------------
  const start = useCallback(() => {
    if (status !== "idle") return

    const token = readAccessToken()
    if (!token) {
      onErrorRef.current?.("请先登录后使用语音输入")
      return
    }

    const wsUrl = `${wsBaseUrl()}${ASR_PATH}?token=${encodeURIComponent(token)}`
    let ws: WebSocket
    try {
      ws = new WebSocket(wsUrl)
      ws.binaryType = "arraybuffer"
    } catch {
      onErrorRef.current?.("无法启动语音输入")
      return
    }
    wsRef.current = ws
    setStatus("connecting")

    ws.onopen = () => {
      setStatus("recording")
      void openMic(ws).catch((e) => {
        fail(mapMicError(e))
      })
    }

    ws.onmessage = (ev) => {
      if (typeof ev.data !== "string") return
      let msg: { text?: string; error?: string }
      try {
        msg = JSON.parse(ev.data)
      } catch {
        return
      }
      if (msg.error) {
        onErrorRef.current?.(msg.error)
      } else if (typeof msg.text === "string") {
        onTextRef.current?.(msg.text)
      }
    }

    ws.onerror = () => {
      onErrorRef.current?.("语音识别连接异常")
    }

    ws.onclose = () => {
      stopMic()
      if (maxTimerRef.current) {
        clearTimeout(maxTimerRef.current)
        maxTimerRef.current = null
      }
      if (stopTimerRef.current) {
        clearTimeout(stopTimerRef.current)
        stopTimerRef.current = null
      }
      wsRef.current = null
      setStatus("idle")
    }
  }, [fail, status, openMic, stopMic])

  // -- 停止 -------------------------------------------------------------
  const stop = useCallback(() => {
    if (!recordingRef.current && status !== "connecting") return
    recordingRef.current = false
    if (maxTimerRef.current) {
      clearTimeout(maxTimerRef.current)
      maxTimerRef.current = null
    }
    stopMic()

    const ws = wsRef.current
    if (ws && ws.readyState === WebSocket.OPEN) {
      setStatus("stopping")
      try {
        ws.send(JSON.stringify({ action: "stop" }))
      } catch {
        // ignore
      }
      stopTimerRef.current = setTimeout(() => {
        teardown()
      }, 5000)
    } else {
      teardown()
    }
  }, [status, stopMic, teardown])

  // 同步最新 stop 到 ref（供 openMic 60s timer 使用），必须在 effect 中赋值
  useEffect(() => { stopFnRef.current = stop }, [stop])

  // 卸载清理
  useEffect(() => () => teardown(), [teardown])

  return { status, start, stop }
}

// ---------------------------------------------------------------------------
// 工具
// ---------------------------------------------------------------------------

function readAccessToken(): string | null {
  try {
    const raw = localStorage.getItem("line_auth_tokens")
    if (!raw) return null
    const parsed = JSON.parse(raw) as { accessToken?: string }
    return parsed.accessToken ?? null
  } catch {
    return null
  }
}

/** 推导 WS 基址：优先 NEXT_PUBLIC_API_BASE_URL（http→ws），否则同源 window.location。 */
function wsBaseUrl(): string {
  const envBase = process.env.NEXT_PUBLIC_API_BASE_URL
  if (envBase && !/backend:/.test(envBase)) {
    return envBase.replace(/^http/, "ws")
  }
  const proto = typeof window !== "undefined" && window.location.protocol === "https:" ? "wss:" : "ws:"
  return `${proto}//${typeof window !== "undefined" ? window.location.host : ""}`
}

/** Float32 PCM → Int16 LE 的 ArrayBuffer（DashScope PCM16 帧格式）。 */
function float32ToPcm16(samples: Float32Array): ArrayBuffer {
  const buffer = new ArrayBuffer(samples.length * 2)
  const view = new DataView(buffer)
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(i * 2, s < 0 ? s * 0x8000 : s * 0x7fff, true)
  }
  return buffer
}

/** 线性插值重采样（语音识别场景足够）。 */
function resample(samples: Float32Array, fromRate: number, toRate: number): Float32Array {
  if (fromRate === toRate) return samples
  const ratio = fromRate / toRate
  const newLength = Math.round(samples.length / ratio)
  const out = new Float32Array(newLength)
  for (let i = 0; i < newLength; i++) {
    const idx = i * ratio
    const left = Math.floor(idx)
    const right = Math.min(left + 1, samples.length - 1)
    const frac = idx - left
    out[i] = samples[left] * (1 - frac) + samples[right] * frac
  }
  return out
}

function mapMicError(e: unknown): string {
  if (e instanceof DOMException) {
    if (e.name === "NotAllowedError" || e.name === "SecurityError") return "麦克风权限被拒绝"
    if (e.name === "NotFoundError" || e.name === "DevicesNotFoundError") return "未找到麦克风设备"
    return e.message || "无法启动录音"
  }
  return e instanceof Error ? e.message : "无法启动录音"
}
