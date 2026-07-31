"use client"

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useAuth } from "@/components/auth/auth-context"
import { adminService } from "@/lib/api/admin"
import { ApiError } from "@/lib/api/client"
import type { SystemSettings } from "@/lib/types/admin"

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between border-b border-slate-100 py-3 last:border-0">
      <span className="text-sm text-slate-500">{label}</span>
      <span className="text-sm font-medium text-slate-800">{value}</span>
    </div>
  )
}

export default function AdminSettingsPage() {
  const { tokens, user } = useAuth()
  const isSuperAdmin = user?.role === "SUPER_ADMIN"
  const [settings, setSettings] = useState<SystemSettings | null>(null)
  const [error, setError] = useState("")
  const [message, setMessage] = useState("")

  const [minLength, setMinLength] = useState("")
  const [announcement, setAnnouncement] = useState("")
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!tokens?.accessToken) return
    adminService
      .getSettings(tokens.accessToken)
      .then((s) => {
        setSettings(s)
        setMinLength(String(s.password.minLength))
        setAnnouncement(s.announcement || "")
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "加载失败"))
  }, [tokens?.accessToken])

  const handleSave = async () => {
    setSaving(true)
    setError("")
    setMessage("")
    try {
      if (!tokens?.accessToken) return
      await adminService.updateSettings(tokens.accessToken, {
        passwordMinLength: Number(minLength),
        announcement,
      })
      setMessage("配置已保存")
      const fresh = await adminService.getSettings(tokens.accessToken)
      setSettings(fresh)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "保存失败")
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-900">系统配置</h1>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}
      {message && <div className="rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-600">{message}</div>}

      {settings && (
        <div className="space-y-6">
          {/* 可编辑区（仅超级管理员） */}
          {isSuperAdmin ? (
            <div className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
              <div className="text-sm font-semibold text-slate-800">可修改配置</div>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <div className="space-y-1.5">
                  <label className="text-xs text-slate-500">密码最小长度（6~64）</label>
                  <Input value={minLength} onChange={(e) => setMinLength(e.target.value)} className="h-9" />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs text-slate-500">站点公告</label>
                  <Input value={announcement} onChange={(e) => setAnnouncement(e.target.value)} className="h-9" placeholder="留空则不展示" />
                </div>
              </div>
              <Button onClick={handleSave} disabled={saving}>
                {saving ? "保存中..." : "保存配置"}
              </Button>
            </div>
          ) : (
            <div className="rounded-lg bg-slate-50 px-4 py-3 text-sm text-slate-500">
              仅超级管理员可修改系统配置。
            </div>
          )}

          {/* 只读展示 */}
          <div className="rounded-xl border border-slate-200 bg-white p-5">
            <div className="mb-2 text-sm font-semibold text-slate-800">当前配置</div>
            <Field label="密码最小长度" value={String(settings.password.minLength)} />
            <Field label="BCrypt 强度" value={String(settings.password.bcryptStrength)} />
            <Field label="注册状态" value={`${settings.registration.enabled ? "开放" : "关闭"} · ${settings.registration.mode}`} />
            <Field label="验证码位数" value={String(settings.verification.codeLength)} />
            <Field label="验证码有效期" value={settings.verification.ttl} />
            <Field label="验证码最大尝试" value={String(settings.verification.maxAttempts)} />
            <Field label="验证码发送间隔" value={settings.verification.sendInterval} />
            <Field label="每日发送上限" value={String(settings.verification.dailyLimit)} />
            <Field label="Access Token 有效期" value={settings.jwt.accessTokenTtl} />
            <Field label="Refresh Token 有效期" value={settings.jwt.refreshTokenTtl} />
          </div>
        </div>
      )}

      {!settings && !error && <div className="text-sm text-slate-400">加载中...</div>}
    </div>
  )
}
