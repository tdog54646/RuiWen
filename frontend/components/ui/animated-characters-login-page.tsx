"use client"

import { useState } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { ArrowLeft, Eye, EyeOff } from "lucide-react"
import { useAuth } from "@/components/auth/auth-context"
import { GoogleLoginButton } from "@/components/auth/google-login-button"
import { LineLogo } from "@/components/brand/line-logo"
import { ApiError } from "@/lib/api/client"
import {
  AuthShell,
  MessageBanner,
  glassInputClass,
} from "@/components/ui/studio"
import { cn } from "@/lib/utils"
import type { IdentifierType, LoginRequest } from "@/lib/types/auth"

function detectIdentifierType(value: string): IdentifierType {
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return "EMAIL"
  if (/^\+?\d{7,15}$/.test(value.replace(/[\s-]/g, ""))) return "PHONE"
  return "EMAIL"
}

export interface LoginPageProps {
  onLoginSuccess?: () => void
}

function LoginPage({ onLoginSuccess }: LoginPageProps) {
  const { login } = useAuth()
  const [showPassword, setShowPassword] = useState(false)
  const [identifier, setIdentifier] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [isLoading, setIsLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError("")
    setIsLoading(true)

    try {
      const identifierType = detectIdentifierType(identifier)
      const payload: LoginRequest = {
        identifierType,
        identifier: identifier.trim(),
        password,
      }
      await login(payload)
      onLoginSuccess?.()
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError("登录失败，请稍后重试")
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthShell>
      {/* 品牌 */}
      <div className="mb-8 flex flex-col items-center text-center">
        <LineLogo className="w-44" priority />
        <h1 className="text-gradient mt-3 text-2xl font-bold tracking-tight">
          欢迎回来
        </h1>
        <p className="mt-1.5 text-sm text-slate-500">
          请输入你的账号信息登录
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
          <Label htmlFor="password" className="text-xs text-slate-500">
            密码
          </Label>
          <div className="relative">
            <Input
              id="password"
              type={showPassword ? "text" : "password"}
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
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

        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Checkbox id="remember" />
            <Label
              htmlFor="remember"
              className="cursor-pointer text-sm font-normal text-slate-600"
            >
              记住登录 30 天
            </Label>
          </div>
          <Link
            href="/reset-password"
            className="text-sm font-medium text-violet-600 hover:text-violet-700"
          >
            忘记密码？
          </Link>
        </div>

        <div className="pt-1">
          <MessageBanner tone="error" show={!!error}>
            {error}
          </MessageBanner>
        </div>

        <Button
          type="submit"
          size="lg"
          disabled={isLoading}
          className="h-12 w-full bg-gradient-to-r from-cyan-500 to-violet-600 text-base font-medium text-white shadow-lg shadow-violet-500/25"
        >
          {isLoading ? "登录中..." : "登录"}
        </Button>
      </form>

      <div className="mt-6 flex flex-col items-center gap-3">
        <span className="text-xs text-slate-400">或使用 Google 账号登录</span>
        <GoogleLoginButton
          onSuccess={onLoginSuccess}
          onError={(msg) => setError(msg)}
        />
      </div>

      <div className="mt-8 text-center text-sm text-slate-500">
        还没有账号？{" "}
        <Link
          href="/register"
          className="font-medium text-violet-600 hover:text-violet-700"
        >
          立即注册
        </Link>
      </div>

      <div className="mt-6 text-center">
        <Link
          href="/app"
          className="inline-flex items-center gap-1.5 text-xs text-slate-400 transition-colors hover:text-slate-600"
        >
          <ArrowLeft className="size-3.5" />
          返回主页
        </Link>
      </div>
    </AuthShell>
  )
}

export const Component = LoginPage
