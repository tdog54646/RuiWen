"use client"

import { AuthProviderWrapper } from "@/components/auth/auth-provider"
import { TopNav } from "@/components/ui/top-nav"

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthProviderWrapper>
      <div className="app-canvas relative min-h-dvh">
        <a href="#main-content" className="skip-link">
          跳到主要内容
        </a>
        <TopNav />
        <main
          id="main-content"
          className="relative mx-auto w-full max-w-[1280px] px-4 pb-16 pt-36 sm:px-6 md:pt-28 lg:px-8"
        >
          {children}
        </main>
      </div>
    </AuthProviderWrapper>
  )
}
