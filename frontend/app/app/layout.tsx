"use client"

import { AuthProviderWrapper } from "@/components/auth/auth-provider"
import { SiteFooter } from "@/components/layout/site-footer"
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
        <div className="relative mx-auto w-full max-w-[1280px] border-t border-[#deded8] px-4 py-8 sm:px-6 lg:px-8">
          <SiteFooter />
        </div>
      </div>
    </AuthProviderWrapper>
  )
}
