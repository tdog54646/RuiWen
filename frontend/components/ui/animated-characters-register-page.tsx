"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Eye, EyeOff } from "lucide-react"
import { useAuth } from "@/components/auth/auth-context"
import { LineLogo } from "@/components/brand/line-logo"
import { authService } from "@/lib/api/auth"
import { ApiError } from "@/lib/api/client"
import {
  AuthShell,
  MessageBanner,
  glassInputClass,
} from "@/components/ui/studio"
import { cn } from "@/lib/utils"
import type { RegistrationConfig, RegisterRequest } from "@/lib/types/auth"

export interface RegisterPageProps {
  onRegisterSuccess?: () => void
}

type LoadingState = "loading" | "ready" | "disabled"

function RegisterPage({ onRegisterSuccess }: RegisterPageProps) {
  const { register } = useAuth()
  const [config, setConfig] = useState<RegistrationConfig | null>(null)
  const [configState, setConfigState] = useState<LoadingState>("loading")

  const [showPassword, setShowPassword] = useState(false)
  const [identifier, setIdentifier] = useState("")
  const [code, setCode] = useState("")
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [agreeTerms, setAgreeTerms] = useState(false)
  const [error, setError] = useState("")
  const [message, setMessage] = useState("")
  const [isLoading, setIsLoading] = useState(false)
  const [sendingCode, setSendingCode] = useState(false)
  const [countdown, setCountdown] = useState(0)

  // 首屏拉取注册策略，决定渲染邮箱+密码 还是 手机号+验证码
  useEffect(() => {
    let active = true
    authService
      .getRegistrationConfig()
      .then((cfg) => {
        if (!active) return
        setConfig(cfg)
        setConfigState(cfg.enabled ? "ready" : "disabled")
      })
      .catch(() => {
        if (!active) return
        setConfigState("ready")
      })
    return () => {
      active = false
    }
  }, [])

  const mode = config?.mode ?? "PHONE_CODE"
  const isEmailMode = mode === "EMAIL_PASSWORD"

  useEffect(() => {
    if (countdown <= 0) return
    const timer = setTimeout(() => setCountdown((p) => p - 1), 1000)
    return () => clearTimeout(timer)
  }, [countdown])

  const handleSendCode = async () => {
    if (!identifier) {
      setError("请先填写手机号")
      return
    }
    setError("")
    setMessage("")
    setSendingCode(true)
    try {
      await authService.sendCode({
        scene: "REGISTER",
        identifierType: "PHONE",
        identifier: identifier.trim(),
      })
      setMessage("验证码已发送，请注意查收")
      setCountdown(60)
    } catch (err) {
      setError(err instanceof Error ? err.message : "验证码发送失败")
    } finally {
      setSendingCode(false)
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError("")
    setMessage("")

    if (isEmailMode) {
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(identifier)) {
        setError("请输入合法的邮箱")
        return
      }
      if (!password) {
        setError("请设置密码")
        return
      }
      if (password !== confirmPassword) {
        setError("两次输入的密码不一致")
        return
      }
    } else {
      if (!identifier) {
        setError("请输入手机号")
        return
      }
      if (!code) {
        setError("请输入验证码")
        return
      }
    }

    setIsLoading(true)
    try {
      const payload: RegisterRequest = {
        identifierType: isEmailMode ? "EMAIL" : "PHONE",
        identifier: identifier.trim(),
        code: isEmailMode ? undefined : code,
        password: isEmailMode ? password : undefined,
        agreeTerms,
      }
      await register(payload)
      setMessage("注册成功，已自动登录")
      setTimeout(() => onRegisterSuccess?.(), 400)
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError("注册失败，请稍后重试")
      }
    } finally {
      setIsLoading(false)
    }
  }

  // 注册策略加载中
  if (configState === "loading") {
    return (
      <AuthShell>
        <div className="flex h-40 items-center justify-center text-sm text-slate-400">
          加载中...
        </div>
      </AuthShell>
    )
  }

  // 注册已关闭
  if (configState === "disabled") {
    return (
      <AuthShell>
        <div className="mb-8 flex flex-col items-center text-center">
          <LineLogo className="w-44" priority />
          <h1 className="text-gradient mt-3 text-2xl font-bold tracking-tight">加入 Line</h1>
        </div>
        <MessageBanner tone="error" show>
          注册功能暂未开放，请联系管理员。
        </MessageBanner>
        <div className="mt-8 text-center text-sm text-slate-500">
          已有账号？{" "}
          <Link href="/login" className="font-medium text-violet-600 hover:text-violet-700">
            返回登录
          </Link>
        </div>
      </AuthShell>
    )
  }

  const submitDisabled =
    isLoading ||
    !identifier ||
    !agreeTerms ||
    (isEmailMode ? !password || !confirmPassword : !code)

  return (
    <AuthShell>
      <div className="mb-8 flex flex-col items-center text-center">
        <LineLogo className="w-44" priority />
        <h1 className="text-gradient mt-3 text-2xl font-bold tracking-tight">加入 Line</h1>
        <p className="mt-1.5 text-sm text-slate-500">
          {isEmailMode ? "使用邮箱与密码完成注册" : "使用手机号与验证码完成注册"}
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="identifier" className="text-xs text-slate-500">
            {isEmailMode ? "邮箱" : "手机号"}
          </Label>
          <Input
            id="identifier"
            type="text"
            placeholder={isEmailMode ? "请输入邮箱" : "请输入手机号"}
            value={identifier}
            autoComplete={isEmailMode ? "email" : "tel"}
            onChange={(e) => setIdentifier(e.target.value)}
            required
            className={cn(glassInputClass, "h-12")}
          />
        </div>

        {/* 手机号模式：验证码 */}
        {!isEmailMode && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="code" className="text-xs text-slate-500">
              验证码
            </Label>
            <div className="flex gap-2">
              <Input
                id="code"
                className={cn(glassInputClass, "h-12 flex-1")}
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="请输入验证码"
                autoComplete="one-time-code"
                required
              />
              <Button
                type="button"
                variant="outline"
                className="h-12 shrink-0 border-white/60 bg-white/60 backdrop-blur-md"
                disabled={sendingCode || countdown > 0}
                onClick={handleSendCode}
              >
                {countdown > 0 ? `${countdown}s` : "获取验证码"}
              </Button>
            </div>
            <p className="text-xs text-slate-400">验证码用于验证账号所有权，有效期有限。</p>
          </div>
        )}

        {/* 邮箱模式：密码 + 确认密码 */}
        {isEmailMode && (
          <>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="password" className="text-xs text-slate-500">
                登录密码
              </Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  placeholder="请设置不少于 8 位的密码"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="new-password"
                  required
                  className={cn(glassInputClass, "h-12 pr-11")}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 transition-colors hover:text-slate-600"
                >
                  {showPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
                </button>
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="confirmPassword" className="text-xs text-slate-500">
                再次输入密码
              </Label>
              <Input
                id="confirmPassword"
                type={showPassword ? "text" : "password"}
                placeholder="请再次输入密码"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                autoComplete="new-password"
                required
                className={cn(glassInputClass, "h-12")}
              />
            </div>
          </>
        )}

        <div className="flex items-start gap-2">
          <Checkbox
            id="agreeTerms"
            checked={agreeTerms}
            onCheckedChange={(checked) => setAgreeTerms(!!checked)}
          />
          <Label
            htmlFor="agreeTerms"
            className="cursor-pointer pt-0.5 text-sm font-normal leading-relaxed text-slate-600"
          >
            我已阅读并同意
            <a href="#" className="text-violet-600" onClick={(e) => e.preventDefault()}>
              《用户协议》
            </a>
            和
            <a href="#" className="text-violet-600" onClick={(e) => e.preventDefault()}>
              《隐私政策》
            </a>
          </Label>
        </div>

        <div className="flex flex-col gap-2 pt-1">
          <MessageBanner tone="error" show={!!error}>
            {error}
          </MessageBanner>
          <MessageBanner tone="success" show={!!message}>
            {message}
          </MessageBanner>
        </div>

        <Button
          type="submit"
          size="lg"
          disabled={submitDisabled}
          className="h-12 w-full bg-gradient-to-r from-cyan-500 to-violet-600 text-base font-medium text-white shadow-lg shadow-violet-500/25"
        >
          {isLoading ? "注册中..." : "立即注册"}
        </Button>
      </form>

      <div className="mt-8 text-center text-sm text-slate-500">
        已有账号？{" "}
        <Link href="/login" className="font-medium text-violet-600 hover:text-violet-700">
          返回登录
        </Link>
      </div>
    </AuthShell>
  )
}

export const Component = RegisterPage
