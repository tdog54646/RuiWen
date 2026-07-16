"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Eye, EyeOff } from "lucide-react"
import { LineLogo } from "@/components/brand/line-logo"
import { authService } from "@/lib/api/auth"
import { ApiError } from "@/lib/api/client"
import {
  AuthShell,
  MessageBanner,
  glassInputClass,
} from "@/components/ui/studio"
import { cn } from "@/lib/utils"
import type { IdentifierType } from "@/lib/types/auth"

function detectIdentifierType(value: string): IdentifierType {
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return "EMAIL"
  if (/^\+?\d{7,15}$/.test(value.replace(/[\s-]/g, ""))) return "PHONE"
  return "PHONE"
}

export interface ResetPasswordPageProps {
  onResetSuccess?: () => void
}

function ResetPasswordPage({ onResetSuccess }: ResetPasswordPageProps) {
  const [showPassword, setShowPassword] = useState(false)
  const [identifier, setIdentifier] = useState("")
  const [code, setCode] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [error, setError] = useState("")
  const [message, setMessage] = useState("")
  const [isLoading, setIsLoading] = useState(false)
  const [sendingCode, setSendingCode] = useState(false)
  const [countdown, setCountdown] = useState(0)

  useEffect(() => {
    if (countdown <= 0) return
    const timer = setTimeout(() => setCountdown((p) => p - 1), 1000)
    return () => clearTimeout(timer)
  }, [countdown])

  const handleSendCode = async () => {
    if (!identifier) {
      setError("请先填写邮箱或手机号")
      return
    }
    setError("")
    setMessage("")
    setSendingCode(true)
    try {
      const identifierType = detectIdentifierType(identifier)
      await authService.sendCode({
        scene: "RESET_PASSWORD",
        identifierType,
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
    if (newPassword !== confirmPassword) {
      setError("两次输入的密码不一致")
      return
    }
    setIsLoading(true)

    try {
      const identifierType = detectIdentifierType(identifier)
      await authService.resetPassword({
        identifierType,
        identifier: identifier.trim(),
        code,
        newPassword,
      })
      setMessage("密码重置成功，请返回登录")
      setTimeout(() => onResetSuccess?.(), 800)
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError("重置失败，请稍后重试")
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthShell>
      <div className="mb-8 flex flex-col items-center text-center">
        <LineLogo className="w-44" priority />
        <h1 className="text-gradient mt-3 text-2xl font-bold tracking-tight">
          重置密码
        </h1>
        <p className="mt-1.5 text-sm text-slate-500">
          输入邮箱/手机号与验证码重置你的密码
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="identifier" className="text-xs text-slate-500">
            邮箱 / 手机号
          </Label>
          <Input
            id="identifier"
            type="text"
            placeholder="请输入邮箱或手机号"
            value={identifier}
            autoComplete="username"
            onChange={(e) => setIdentifier(e.target.value)}
            required
            className={cn(glassInputClass, "h-12")}
          />
        </div>

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
          <p className="text-xs text-slate-400">验证码用于校验身份，有效期有限。</p>
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="newPassword" className="text-xs text-slate-500">
            新密码
          </Label>
          <div className="relative">
            <Input
              id="newPassword"
              type={showPassword ? "text" : "password"}
              placeholder="请设置不少于 8 位的新密码"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password"
              required
              className={cn(glassInputClass, "h-12 pr-11")}
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 transition-colors hover:text-slate-600"
            >
              {showPassword ? (
                <EyeOff className="size-5" />
              ) : (
                <Eye className="size-5" />
              )}
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
            placeholder="请再次输入新密码"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            autoComplete="new-password"
            required
            className={cn(glassInputClass, "h-12")}
          />
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
          disabled={
            isLoading || !identifier || !code || !newPassword || !confirmPassword
          }
          className="h-12 w-full bg-gradient-to-r from-cyan-500 to-violet-600 text-base font-medium text-white shadow-lg shadow-violet-500/25"
        >
          {isLoading ? "重置中..." : "重置密码"}
        </Button>
      </form>

      <div className="mt-8 text-center text-sm text-slate-500">
        想起密码了？{" "}
        <Link
          href="/login"
          className="font-medium text-violet-600 hover:text-violet-700"
        >
          返回登录
        </Link>
      </div>

      <div className="mt-6 flex items-center justify-center gap-1.5 text-center text-xs text-slate-400">
        <LineLogo variant="mark" className="size-4" />
        Line · 知识分享平台
      </div>
    </AuthShell>
  )
}

export const Component = ResetPasswordPage
