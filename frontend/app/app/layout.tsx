"use client"

import { AuthProviderWrapper } from "@/components/auth/auth-provider"
import { SidebarDemo } from "@/components/ui/sidebar-demo"

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthProviderWrapper>
      <div className="mx-auto flex min-h-dvh w-full max-w-[1400px] gap-6 p-4 md:p-6">
        <SidebarDemo />
        <div className="flex flex-1 flex-col gap-6 overflow-hidden pt-16 md:pt-0">
          {children}
        </div>
      </div>
    </AuthProviderWrapper>
  )
}
