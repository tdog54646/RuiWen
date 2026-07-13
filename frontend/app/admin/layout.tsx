"use client"

import { AuthProviderWrapper } from "@/components/auth/auth-provider"
import { AdminShell } from "@/components/admin/admin-shell"

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthProviderWrapper>
      <AdminShell>{children}</AdminShell>
    </AuthProviderWrapper>
  )
}
