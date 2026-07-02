"use client"

import { useRouter } from "next/navigation"
import { AuthProviderWrapper } from "@/components/auth/auth-provider"
import { Component as RegisterPage } from "@/components/ui/animated-characters-register-page"

function RegisterInner() {
  const router = useRouter()

  return (
    <RegisterPage
      onRegisterSuccess={() => {
        router.replace("/app")
      }}
    />
  )
}

export default function RegisterRoute() {
  return (
    <AuthProviderWrapper>
      <RegisterInner />
    </AuthProviderWrapper>
  )
}
