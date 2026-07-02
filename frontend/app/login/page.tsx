"use client"

import { Suspense } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { AuthProviderWrapper } from "@/components/auth/auth-provider"
import { Component as LoginPage } from "@/components/ui/animated-characters-login-page"

function LoginInner() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const next = searchParams.get("next") ?? "/app"

  return (
    <LoginPage
      onLoginSuccess={() => {
        router.replace(next)
      }}
    />
  )
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
