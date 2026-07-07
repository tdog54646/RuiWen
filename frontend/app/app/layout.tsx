"use client"

import { AuthProviderWrapper } from "@/components/auth/auth-provider"
import { AuroraBackground } from "@/components/ui/studio"
import { TopNav } from "@/components/ui/top-nav"

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthProviderWrapper>
      <div className="relative min-h-dvh">
        <AuroraBackground className="fixed inset-0" />
        <TopNav />
        <main className="relative mx-auto w-full max-w-[1120px] px-4 pb-12 pt-24 md:pt-28">
          {children}
        </main>
      </div>
    </AuthProviderWrapper>
  )
}
