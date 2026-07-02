"use client"

import { useRouter } from "next/navigation"
import { AuthProviderWrapper } from "@/components/auth/auth-provider"
import { Component as ResetPasswordComponent } from "@/components/ui/animated-characters-reset-password-page"

function ResetInner() {
  const router = useRouter()
  return <ResetPasswordComponent onResetSuccess={() => router.replace("/login")} />
}

export default function ResetPasswordPage() {
  return (
    <AuthProviderWrapper>
      <ResetInner />
    </AuthProviderWrapper>
  )
}
