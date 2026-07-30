"use client"

import { Suspense } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { AuthProviderWrapper } from "@/components/auth/auth-provider"
import { Component as LoginPage } from "@/components/ui/animated-characters-login-page"

function LoginInner() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const next = sanitizeNextPath(searchParams.get("next"))

  return (
    <LoginPage
      onLoginSuccess={() => {
        router.replace(next)
      }}
    />
  )
}

function sanitizeNextPath(value: string | null): string {
  if (
    !value ||
    !value.startsWith("/") ||
    value.startsWith("//") ||
    value.includes("\\") ||
    /[\u0000-\u001f\u007f]/.test(value)
  ) {
    return "/app"
  }
  try {
    const base = "https://ruiwen.invalid"
    const resolved = new URL(value, base)
    if (resolved.origin !== base) return "/app"
    return `${resolved.pathname}${resolved.search}${resolved.hash}`
  } catch {
    return "/app"
  }
}

export default function LoginRoute() {
  return (
    <AuthProviderWrapper>
      <Suspense>
        <LoginInner />
      </Suspense>
    </AuthProviderWrapper>
  )
}
