"use client"

import { useCallback, useEffect, useRef } from "react"
import Script from "next/script"
import { useAuth } from "@/components/auth/auth-context"

// GIS 回调返回的凭证响应
type CredentialResponse = {
  credential: string
}

// Google Identity Services 客户端类型（仅声明用到的部分）
declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: Record<string, unknown>) => void
          renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void
        }
      }
    }
  }
}

/**
 * Google 登录按钮。
 *
 * <p>加载 Google Identity Services，渲染官方登录按钮；用户授权后拿到 ID Token，
 * 调用 {@link useAuth().loginWithGoogle} 换取本系统令牌。成功后回调 onSuccess，
 * 失败回传错误信息给 onError。</p>
 */
export function GoogleLoginButton({
  onSuccess,
  onError,
}: {
  onSuccess?: () => void
  onError?: (message: string) => void
}) {
  const { loginWithGoogle } = useAuth()
  const containerRef = useRef<HTMLDivElement>(null)
  const initializedRef = useRef(false)
  // 用 ref 持有回调，避免父组件每次渲染触发 GIS 重复初始化
  const onSuccessRef = useRef(onSuccess)
  const onErrorRef = useRef(onError)

  useEffect(() => {
    onSuccessRef.current = onSuccess
  }, [onSuccess])
  useEffect(() => {
    onErrorRef.current = onError
  }, [onError])

  const handleCredential = useCallback(
    async (response: CredentialResponse) => {
      try {
        await loginWithGoogle(response.credential)
        onSuccessRef.current?.()
      } catch (err) {
        const message =
          err instanceof Error ? err.message : "Google 登录失败，请稍后重试"
        onErrorRef.current?.(message)
      }
    },
    [loginWithGoogle],
  )

  useEffect(() => {
    const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID
    if (!clientId) return

    const init = () => {
      if (initializedRef.current) return
      const accounts = window.google?.accounts?.id
      if (!accounts || !containerRef.current) return
      accounts.initialize({
        client_id: clientId,
        callback: handleCredential,
      })
      accounts.renderButton(containerRef.current, {
        theme: "outline",
        size: "large",
        width: 320,
        text: "continue_with",
        shape: "pill",
      })
      initializedRef.current = true
    }

    if (window.google?.accounts?.id) {
      init()
      return
    }
    // GIS 脚本异步加载，轮询等待就绪后初始化
    const timer = window.setInterval(() => {
      if (window.google?.accounts?.id) {
        window.clearInterval(timer)
        init()
      }
    }, 150)
    return () => window.clearInterval(timer)
  }, [handleCredential])

  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID
  return (
    <>
      {clientId && (
        <Script
          src="https://accounts.google.com/gsi/client"
          strategy="afterInteractive"
          async
          defer
        />
      )}
      <div ref={containerRef} />
    </>
  )
}
