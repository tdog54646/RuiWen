"use client"

import { useState, useEffect, useRef } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { ArrowLeft, Eye, EyeOff, Sparkles } from "lucide-react"
import { useAuth } from "@/components/auth/auth-context"
import { ApiError } from "@/lib/api/client"
import type { IdentifierType, LoginRequest } from "@/lib/types/auth"

interface PupilProps {
  size?: number
  maxDistance?: number
  pupilColor?: string
  forceLookX?: number
  forceLookY?: number
}

const Pupil = ({
  size = 12,
  maxDistance = 5,
  pupilColor = "black",
  forceLookX,
  forceLookY,
}: PupilProps) => {
  const [mouseX, setMouseX] = useState<number>(0)
  const [mouseY, setMouseY] = useState<number>(0)
  const pupilRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      setMouseX(e.clientX)
      setMouseY(e.clientY)
    }
    window.addEventListener("mousemove", handleMouseMove)
    return () => window.removeEventListener("mousemove", handleMouseMove)
  }, [])

  const calculatePupilPosition = () => {
    if (!pupilRef.current) return { x: 0, y: 0 }
    if (forceLookX !== undefined && forceLookY !== undefined) {
      return { x: forceLookX, y: forceLookY }
    }
    const pupil = pupilRef.current.getBoundingClientRect()
    const pupilCenterX = pupil.left + pupil.width / 2
    const pupilCenterY = pupil.top + pupil.height / 2
    const deltaX = mouseX - pupilCenterX
    const deltaY = mouseY - pupilCenterY
    const distance = Math.min(
      Math.sqrt(deltaX ** 2 + deltaY ** 2),
      maxDistance,
    )
    const angle = Math.atan2(deltaY, deltaX)
    return { x: Math.cos(angle) * distance, y: Math.sin(angle) * distance }
  }

  const pos = calculatePupilPosition()

  return (
    <div
      ref={pupilRef}
      className="rounded-full"
      style={{
        width: `${size}px`,
        height: `${size}px`,
        backgroundColor: pupilColor,
        transform: `translate(${pos.x}px, ${pos.y}px)`,
        transition: "transform 0.1s ease-out",
      }}
    />
  )
}

interface EyeBallProps {
  size?: number
  pupilSize?: number
  maxDistance?: number
  eyeColor?: string
  pupilColor?: string
  isBlinking?: boolean
  forceLookX?: number
  forceLookY?: number
}

const EyeBall = ({
  size = 48,
  pupilSize = 16,
  maxDistance = 10,
  eyeColor = "white",
  pupilColor = "black",
  isBlinking = false,
  forceLookX,
  forceLookY,
}: EyeBallProps) => {
  const [mouseX, setMouseX] = useState<number>(0)
  const [mouseY, setMouseY] = useState<number>(0)
  const eyeRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      setMouseX(e.clientX)
      setMouseY(e.clientY)
    }
    window.addEventListener("mousemove", handleMouseMove)
    return () => window.removeEventListener("mousemove", handleMouseMove)
  }, [])

  const calculatePupilPosition = () => {
    if (!eyeRef.current) return { x: 0, y: 0 }
    if (forceLookX !== undefined && forceLookY !== undefined) {
      return { x: forceLookX, y: forceLookY }
    }
    const eye = eyeRef.current.getBoundingClientRect()
    const eyeCenterX = eye.left + eye.width / 2
    const eyeCenterY = eye.top + eye.height / 2
    const deltaX = mouseX - eyeCenterX
    const deltaY = mouseY - eyeCenterY
    const distance = Math.min(
      Math.sqrt(deltaX ** 2 + deltaY ** 2),
      maxDistance,
    )
    const angle = Math.atan2(deltaY, deltaX)
    return { x: Math.cos(angle) * distance, y: Math.sin(angle) * distance }
  }

  const pos = calculatePupilPosition()

  return (
    <div
      ref={eyeRef}
      className="flex items-center justify-center rounded-full transition-all duration-150"
      style={{
        width: `${size}px`,
        height: isBlinking ? "2px" : `${size}px`,
        backgroundColor: eyeColor,
        overflow: "hidden",
      }}
    >
      {!isBlinking && (
        <div
          className="rounded-full"
          style={{
            width: `${pupilSize}px`,
            height: `${pupilSize}px`,
            backgroundColor: pupilColor,
            transform: `translate(${pos.x}px, ${pos.y}px)`,
            transition: "transform 0.1s ease-out",
          }}
        />
      )}
    </div>
  )
}

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
  const [mouseX, setMouseX] = useState<number>(0)
  const [mouseY, setMouseY] = useState<number>(0)
  const [isPurpleBlinking, setIsPurpleBlinking] = useState(false)
  const [isBlackBlinking, setIsBlackBlinking] = useState(false)
  const [isTyping, setIsTyping] = useState(false)
  const [isLookingAtEachOther, setIsLookingAtEachOther] = useState(false)
  const [isPurplePeeking, setIsPurplePeeking] = useState(false)
  const purpleRef = useRef<HTMLDivElement>(null)
  const blackRef = useRef<HTMLDivElement>(null)
  const yellowRef = useRef<HTMLDivElement>(null)
  const orangeRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      setMouseX(e.clientX)
      setMouseY(e.clientY)
    }
    window.addEventListener("mousemove", handleMouseMove)
    return () => window.removeEventListener("mousemove", handleMouseMove)
  }, [])

  useEffect(() => {
    const getInterval = () => Math.random() * 4000 + 3000
    const scheduleBlink = () => {
      const t = setTimeout(() => {
        setIsPurpleBlinking(true)
        setTimeout(() => {
          setIsPurpleBlinking(false)
          scheduleBlink()
        }, 150)
      }, getInterval())
      return t
    }
    const t = scheduleBlink()
    return () => clearTimeout(t)
  }, [])

  useEffect(() => {
    const getInterval = () => Math.random() * 4000 + 3000
    const scheduleBlink = () => {
      const t = setTimeout(() => {
        setIsBlackBlinking(true)
        setTimeout(() => {
          setIsBlackBlinking(false)
          scheduleBlink()
        }, 150)
      }, getInterval())
      return t
    }
    const t = scheduleBlink()
    return () => clearTimeout(t)
  }, [])

  useEffect(() => {
    if (isTyping) {
      setIsLookingAtEachOther(true)
      const timer = setTimeout(() => setIsLookingAtEachOther(false), 800)
      return () => clearTimeout(timer)
    } else {
      setIsLookingAtEachOther(false)
    }
  }, [isTyping])

  useEffect(() => {
    if (password.length > 0 && showPassword) {
      const schedulePeek = () => {
        const t = setTimeout(() => {
          setIsPurplePeeking(true)
          setTimeout(() => setIsPurplePeeking(false), 800)
        }, Math.random() * 3000 + 2000)
        return t
      }
      const t = schedulePeek()
      return () => clearTimeout(t)
    } else {
      setIsPurplePeeking(false)
    }
  }, [password, showPassword, isPurplePeeking])

  const calculatePosition = (ref: React.RefObject<HTMLDivElement | null>) => {
    if (!ref.current) return { faceX: 0, faceY: 0, bodySkew: 0 }
    const rect = ref.current.getBoundingClientRect()
    const centerX = rect.left + rect.width / 2
    const centerY = rect.top + rect.height / 3
    const deltaX = mouseX - centerX
    const deltaY = mouseY - centerY
    const faceX = Math.max(-15, Math.min(15, deltaX / 20))
    const faceY = Math.max(-10, Math.min(10, deltaY / 30))
    const bodySkew = Math.max(-6, Math.min(6, -deltaX / 120))
    return { faceX, faceY, bodySkew }
  }

  const purplePos = calculatePosition(purpleRef)
  const blackPos = calculatePosition(blackRef)
  const yellowPos = calculatePosition(yellowRef)
  const orangePos = calculatePosition(orangeRef)

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

  const pwdVisible = password.length > 0 && showPassword
  const pwdHidden = password.length > 0 && !showPassword

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Left — animated characters */}
      <div className="relative hidden flex-col justify-between bg-gradient-to-br from-primary/90 via-primary to-primary/80 p-12 text-primary-foreground lg:flex">
        <div className="relative z-20">
          <div className="flex items-center gap-2 text-lg font-semibold">
            <div className="flex size-8 items-center justify-center rounded-lg bg-primary-foreground/10 backdrop-blur-sm">
              <Sparkles className="size-4" />
            </div>
            <span>Line</span>
          </div>
        </div>

        <div className="relative z-20 flex h-[500px] items-end justify-center">
          <div className="relative" style={{ width: "550px", height: "400px" }}>
            {/* Purple */}
            <div
              ref={purpleRef}
              className="absolute bottom-0 transition-all duration-700 ease-in-out"
              style={{
                left: "70px",
                width: "180px",
                height: isTyping || pwdHidden ? "440px" : "400px",
                backgroundColor: "#6C3FF5",
                borderRadius: "10px 10px 0 0",
                zIndex: 1,
                transform: pwdVisible
                  ? "skewX(0deg)"
                  : isTyping || pwdHidden
                    ? `skewX(${(purplePos.bodySkew || 0) - 12}deg) translateX(40px)`
                    : `skewX(${purplePos.bodySkew || 0}deg)`,
                transformOrigin: "bottom center",
              }}
            >
              <div
                className="absolute flex gap-8 transition-all duration-700 ease-in-out"
                style={{
                  left: pwdVisible ? "20px" : isLookingAtEachOther ? "55px" : `${45 + purplePos.faceX}px`,
                  top: pwdVisible ? "35px" : isLookingAtEachOther ? "65px" : `${40 + purplePos.faceY}px`,
                }}
              >
                <EyeBall size={18} pupilSize={7} maxDistance={5} eyeColor="white" pupilColor="#2D2D2D" isBlinking={isPurpleBlinking} forceLookX={pwdVisible ? (isPurplePeeking ? 4 : -4) : isLookingAtEachOther ? 3 : undefined} forceLookY={pwdVisible ? (isPurplePeeking ? 5 : -4) : isLookingAtEachOther ? 4 : undefined} />
                <EyeBall size={18} pupilSize={7} maxDistance={5} eyeColor="white" pupilColor="#2D2D2D" isBlinking={isPurpleBlinking} forceLookX={pwdVisible ? (isPurplePeeking ? 4 : -4) : isLookingAtEachOther ? 3 : undefined} forceLookY={pwdVisible ? (isPurplePeeking ? 5 : -4) : isLookingAtEachOther ? 4 : undefined} />
              </div>
            </div>

            {/* Black */}
            <div
              ref={blackRef}
              className="absolute bottom-0 transition-all duration-700 ease-in-out"
              style={{
                left: "240px",
                width: "120px",
                height: "310px",
                backgroundColor: "#2D2D2D",
                borderRadius: "8px 8px 0 0",
                zIndex: 2,
                transform: pwdVisible
                  ? "skewX(0deg)"
                  : isLookingAtEachOther
                    ? `skewX(${(blackPos.bodySkew || 0) * 1.5 + 10}deg) translateX(20px)`
                    : isTyping || pwdHidden
                      ? `skewX(${(blackPos.bodySkew || 0) * 1.5}deg)`
                      : `skewX(${blackPos.bodySkew || 0}deg)`,
                transformOrigin: "bottom center",
              }}
            >
              <div
                className="absolute flex gap-6 transition-all duration-700 ease-in-out"
                style={{
                  left: pwdVisible ? "10px" : isLookingAtEachOther ? "32px" : `${26 + blackPos.faceX}px`,
                  top: pwdVisible ? "28px" : isLookingAtEachOther ? "12px" : `${32 + blackPos.faceY}px`,
                }}
              >
                <EyeBall size={16} pupilSize={6} maxDistance={4} eyeColor="white" pupilColor="#2D2D2D" isBlinking={isBlackBlinking} forceLookX={pwdVisible ? -4 : isLookingAtEachOther ? 0 : undefined} forceLookY={pwdVisible ? -4 : isLookingAtEachOther ? -4 : undefined} />
                <EyeBall size={16} pupilSize={6} maxDistance={4} eyeColor="white" pupilColor="#2D2D2D" isBlinking={isBlackBlinking} forceLookX={pwdVisible ? -4 : isLookingAtEachOther ? 0 : undefined} forceLookY={pwdVisible ? -4 : isLookingAtEachOther ? -4 : undefined} />
              </div>
            </div>

            {/* Orange */}
            <div
              ref={orangeRef}
              className="absolute bottom-0 transition-all duration-700 ease-in-out"
              style={{
                left: "0px",
                width: "240px",
                height: "200px",
                zIndex: 3,
                backgroundColor: "#FF9B6B",
                borderRadius: "120px 120px 0 0",
                transform: pwdVisible ? "skewX(0deg)" : `skewX(${orangePos.bodySkew || 0}deg)`,
                transformOrigin: "bottom center",
              }}
            >
              <div
                className="absolute flex gap-8 transition-all duration-200 ease-out"
                style={{
                  left: pwdVisible ? "50px" : `${82 + (orangePos.faceX || 0)}px`,
                  top: pwdVisible ? "85px" : `${90 + (orangePos.faceY || 0)}px`,
                }}
              >
                <Pupil size={12} maxDistance={5} pupilColor="#2D2D2D" forceLookX={pwdVisible ? -5 : undefined} forceLookY={pwdVisible ? -4 : undefined} />
                <Pupil size={12} maxDistance={5} pupilColor="#2D2D2D" forceLookX={pwdVisible ? -5 : undefined} forceLookY={pwdVisible ? -4 : undefined} />
              </div>
            </div>

            {/* Yellow */}
            <div
              ref={yellowRef}
              className="absolute bottom-0 transition-all duration-700 ease-in-out"
              style={{
                left: "310px",
                width: "140px",
                height: "230px",
                backgroundColor: "#E8D754",
                borderRadius: "70px 70px 0 0",
                zIndex: 4,
                transform: pwdVisible ? "skewX(0deg)" : `skewX(${yellowPos.bodySkew || 0}deg)`,
                transformOrigin: "bottom center",
              }}
            >
              <div
                className="absolute flex gap-6 transition-all duration-200 ease-out"
                style={{
                  left: pwdVisible ? "20px" : `${52 + (yellowPos.faceX || 0)}px`,
                  top: pwdVisible ? "35px" : `${40 + (yellowPos.faceY || 0)}px`,
                }}
              >
                <Pupil size={12} maxDistance={5} pupilColor="#2D2D2D" forceLookX={pwdVisible ? -5 : undefined} forceLookY={pwdVisible ? -4 : undefined} />
                <Pupil size={12} maxDistance={5} pupilColor="#2D2D2D" forceLookX={pwdVisible ? -5 : undefined} forceLookY={pwdVisible ? -4 : undefined} />
              </div>
              <div
                className="absolute h-[4px] w-20 rounded-full bg-[#2D2D2D] transition-all duration-200 ease-out"
                style={{
                  left: pwdVisible ? "10px" : `${40 + (yellowPos.faceX || 0)}px`,
                  top: pwdVisible ? "88px" : `${88 + (yellowPos.faceY || 0)}px`,
                }}
              />
            </div>
          </div>
        </div>

        <div className="relative z-20 flex items-center gap-8 text-sm text-primary-foreground/60">
          <a href="#" className="transition-colors hover:text-primary-foreground">隐私政策</a>
          <a href="#" className="transition-colors hover:text-primary-foreground">服务条款</a>
          <a href="#" className="transition-colors hover:text-primary-foreground">联系我们</a>
        </div>

        <div className="absolute inset-0 bg-[size:20px_20px] bg-grid-white/[0.05]" />
        <div className="absolute right-1/4 top-1/4 size-64 rounded-full bg-primary-foreground/10 blur-3xl" />
        <div className="absolute bottom-1/4 left-1/4 size-96 rounded-full bg-primary-foreground/5 blur-3xl" />
      </div>

      {/* Right — login form */}
      <div className="relative flex items-center justify-center bg-background p-8">
        <Link
          href="/app"
          className="absolute right-6 top-6 z-10 inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowLeft className="size-4 shrink-0" aria-hidden />
          返回主页
        </Link>
        <div className="w-full max-w-[420px]">
          <div className="mb-12 flex items-center justify-center gap-2 text-lg font-semibold lg:hidden">
            <div className="flex size-8 items-center justify-center rounded-lg bg-primary/10">
              <Sparkles className="size-4 text-primary" />
            </div>
            <span>Line</span>
          </div>

          <div className="mb-10 text-center">
            <h1 className="mb-2 text-3xl font-bold tracking-tight">
              欢迎回来
            </h1>
            <p className="text-sm text-muted-foreground">
              请输入你的账号信息登录
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            <div className="space-y-2">
              <Label htmlFor="identifier" className="text-sm font-medium">
                邮箱 / 手机号
              </Label>
              <Input
                id="identifier"
                type="text"
                placeholder="请输入邮箱或手机号"
                value={identifier}
                autoComplete="username"
                onChange={(e) => setIdentifier(e.target.value)}
                onFocus={() => setIsTyping(true)}
                onBlur={() => setIsTyping(false)}
                required
                className="h-12 border-border/60 bg-background focus:border-primary"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="password" className="text-sm font-medium">
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
                  className="h-12 border-border/60 bg-background pr-10 focus:border-primary"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground"
                >
                  {showPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <Checkbox id="remember" />
                <Label htmlFor="remember" className="cursor-pointer text-sm font-normal">
                  记住登录 30 天
                </Label>
              </div>
              <a href="/reset-password" className="text-sm font-medium text-primary hover:underline">
                忘记密码？
              </a>
            </div>

            {error && (
              <div className="rounded-lg border border-red-900/30 bg-red-950/20 p-3 text-sm text-red-400">
                {error}
              </div>
            )}

            <Button
              type="submit"
              className="h-12 w-full text-base font-medium"
              size="lg"
              disabled={isLoading}
            >
              {isLoading ? "登录中..." : "登录"}
            </Button>
          </form>

          <div className="mt-8 text-center text-sm text-muted-foreground">
            还没有账号？{" "}
            <a href="/register" className="font-medium text-foreground hover:underline">
              立即注册
            </a>
          </div>
        </div>
      </div>
    </div>
  )
}

export const Component = LoginPage
