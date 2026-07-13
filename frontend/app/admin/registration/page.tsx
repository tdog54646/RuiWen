"use client"

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/components/auth/auth-context"
import { adminService } from "@/lib/api/admin"
import { ApiError } from "@/lib/api/client"
import type { RegistrationMode } from "@/lib/types/auth"
import { cn } from "@/lib/utils"
import { Mail, Smartphone, Check } from "lucide-react"

type ModeOption = {
  value: RegistrationMode
  title: string
  desc: string
  icon: typeof Mail
}

const MODE_OPTIONS: ModeOption[] = [
  {
    value: "EMAIL_PASSWORD",
    title: "邮箱 + 密码",
    desc: "用户使用邮箱注册，设置密码即可，无需验证码。",
    icon: Mail,
  },
  {
    value: "PHONE_CODE",
    title: "手机号 + 验证码",
    desc: "用户使用手机号注册，需通过短信验证码校验。",
    icon: Smartphone,
  },
]

export default function AdminRegistrationPage() {
  const { tokens } = useAuth()
  const [enabled, setEnabled] = useState(true)
  const [mode, setMode] = useState<RegistrationMode>("PHONE_CODE")
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")
  const [message, setMessage] = useState("")

  useEffect(() => {
    if (!tokens?.accessToken) return
    adminService
      .getRegistration(tokens.accessToken)
      .then((cfg) => {
        setEnabled(cfg.enabled)
        setMode(cfg.mode)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "加载失败"))
      .finally(() => setLoading(false))
  }, [tokens?.accessToken])

  const handleSave = async () => {
    setSaving(true)
    setError("")
    setMessage("")
    try {
      if (!tokens?.accessToken) return
      const cfg = await adminService.updateRegistration(tokens.accessToken, enabled, mode)
      setEnabled(cfg.enabled)
      setMode(cfg.mode)
      setMessage("注册策略已保存")
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "保存失败")
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-slate-900">注册策略</h1>
        <p className="mt-1 text-sm text-slate-500">
          手动切换网站注册方式。两种模式二选一，注册页将按所选模式自动渲染对应表单。
        </p>
      </div>

      {loading && <div className="text-sm text-slate-400">加载中...</div>}

      {!loading && (
        <div className="space-y-6">
          {/* 启用开关 */}
          <div className="flex items-center justify-between rounded-xl border border-slate-200 bg-white p-5">
            <div>
              <div className="text-sm font-semibold text-slate-800">开放注册</div>
              <div className="mt-1 text-xs text-slate-500">关闭后任何用户都无法注册新账号。</div>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={enabled}
              onClick={() => setEnabled(!enabled)}
              className={cn(
                "relative h-6 w-11 rounded-full transition-colors",
                enabled ? "bg-violet-600" : "bg-slate-300",
              )}
            >
              <span
                className={cn(
                  "absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white transition-transform",
                  enabled && "translate-x-5",
                )}
              />
            </button>
          </div>

          {/* 模式选择 */}
          <div className={cn("space-y-3", !enabled && "pointer-events-none opacity-50")}>
            <div className="text-sm font-semibold text-slate-800">注册方式</div>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              {MODE_OPTIONS.map((opt) => {
                const active = mode === opt.value
                const Icon = opt.icon
                return (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => setMode(opt.value)}
                    className={cn(
                      "relative flex flex-col gap-2 rounded-xl border p-5 text-left transition-all",
                      active
                        ? "border-violet-500 bg-violet-50 ring-1 ring-violet-500"
                        : "border-slate-200 bg-white hover:border-slate-300",
                    )}
                  >
                    <div className="flex items-center gap-2">
                      <Icon className={cn("size-5", active ? "text-violet-600" : "text-slate-500")} />
                      <span className="text-sm font-semibold text-slate-800">{opt.title}</span>
                      {active && <Check className="ml-auto size-4 text-violet-600" />}
                    </div>
                    <p className="text-xs text-slate-500">{opt.desc}</p>
                  </button>
                )
              })}
            </div>
          </div>

          {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}
          {message && <div className="rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-600">{message}</div>}

          <Button onClick={handleSave} disabled={saving} className="bg-slate-900 text-white hover:bg-slate-800">
            {saving ? "保存中..." : "保存策略"}
          </Button>
        </div>
      )}
    </div>
  )
}
