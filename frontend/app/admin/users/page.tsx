"use client"

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { useAuth } from "@/components/auth/auth-context"
import { adminService } from "@/lib/api/admin"
import { ApiError } from "@/lib/api/client"
import type { AdminUserListItem, PageResult } from "@/lib/types/admin"
import { cn } from "@/lib/utils"

const STATUS_BADGE: Record<string, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-700",
  BANNED: "bg-red-100 text-red-700",
}

const ROLE_BADGE: Record<string, string> = {
  USER: "bg-slate-100 text-slate-600",
  ADMIN: "bg-blue-100 text-blue-700",
  SUPER_ADMIN: "bg-violet-100 text-violet-700",
}

const ROLE_OPTIONS: { value: string; label: string; desc: string }[] = [
  { value: "USER", label: "普通用户", desc: "仅可使用前台功能" },
  { value: "ADMIN", label: "管理员", desc: "可访问后台管理" },
  { value: "SUPER_ADMIN", label: "超级管理员", desc: "全部权限，含改角色 / 系统配置" },
]

export default function AdminUsersPage() {
  const { tokens, user } = useAuth()
  const isSuperAdmin = user?.role === "SUPER_ADMIN"

  const [keyword, setKeyword] = useState("")
  const [role, setRole] = useState("")
  const [status, setStatus] = useState("")
  const [page, setPage] = useState(1)
  const [data, setData] = useState<PageResult<AdminUserListItem> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [busyId, setBusyId] = useState<number | null>(null)
  const [resetResult, setResetResult] = useState<{ id: number; password: string } | null>(null)
  const [roleTarget, setRoleTarget] = useState<AdminUserListItem | null>(null)
  const [selectedRole, setSelectedRole] = useState<string>("USER")
  const [roleSaving, setRoleSaving] = useState(false)

  const size = 20

  const load = async () => {
    if (!tokens?.accessToken) return
    setLoading(true)
    setError("")
    try {
      const result = await adminService.listUsers(tokens.accessToken, { keyword, role, status, page, size })
      setData(result)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "加载失败")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tokens?.accessToken, page])

  const run = async (id: number, fn: () => Promise<void>) => {
    setBusyId(id)
    setError("")
    try {
      await fn()
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "操作失败")
    } finally {
      setBusyId(null)
    }
  }

  const openRoleModal = (u: AdminUserListItem) => {
    setRoleTarget(u)
    setSelectedRole(u.role)
    setError("")
  }

  const confirmRoleChange = async () => {
    if (!roleTarget) return
    setRoleSaving(true)
    setError("")
    try {
      await adminService.updateUserRole(tokens!.accessToken, roleTarget.id, selectedRole)
      setRoleTarget(null)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "操作失败")
    } finally {
      setRoleSaving(false)
    }
  }

  const handleResetPassword = async (id: number) => {
    if (!window.confirm("确认重置该用户密码？将生成新密码并强制其下线。")) return
    setBusyId(id)
    setError("")
    try {
      const result = await adminService.resetUserPassword(tokens!.accessToken, id)
      setResetResult({ id, password: result.password })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "操作失败")
    } finally {
      setBusyId(null)
    }
  }

  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-900">用户管理</h1>

      {/* 筛选 */}
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white p-4">
        <Input
          placeholder="搜索昵称/手机号/邮箱"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          className="h-9 w-56"
        />
        <select
          value={role}
          onChange={(e) => setRole(e.target.value)}
          className="h-9 rounded-md border border-slate-200 px-2 text-sm"
        >
          <option value="">全部角色</option>
          <option value="USER">USER</option>
          <option value="ADMIN">ADMIN</option>
          <option value="SUPER_ADMIN">SUPER_ADMIN</option>
        </select>
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          className="h-9 rounded-md border border-slate-200 px-2 text-sm"
        >
          <option value="">全部状态</option>
          <option value="ACTIVE">正常</option>
          <option value="BANNED">封禁</option>
        </select>
        <Button onClick={() => { setPage(1); load() }} className="h-9 bg-slate-900 text-white hover:bg-slate-800">
          查询
        </Button>
      </div>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}
      {resetResult && (
        <div className="flex items-center justify-between rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-700">
          <span>用户 #{resetResult.id} 的新密码：<code className="font-mono">{resetResult.password}</code>（仅显示一次）</span>
          <button onClick={() => setResetResult(null)} className="text-amber-700 underline">关闭</button>
        </div>
      )}

      {/* 表格 */}
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">ID</th>
              <th className="px-4 py-3 font-medium">昵称</th>
              <th className="px-4 py-3 font-medium">手机号</th>
              <th className="px-4 py-3 font-medium">邮箱</th>
              <th className="px-4 py-3 font-medium">角色</th>
              <th className="px-4 py-3 font-medium">状态</th>
              <th className="px-4 py-3 font-medium">注册时间</th>
              <th className="px-4 py-3 font-medium">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading && (
              <tr><td colSpan={8} className="px-4 py-8 text-center text-slate-400">加载中...</td></tr>
            )}
            {!loading && data && data.items.length === 0 && (
              <tr><td colSpan={8} className="px-4 py-8 text-center text-slate-400">暂无数据</td></tr>
            )}
            {!loading && data?.items.map((u) => (
              <tr key={u.id} className="hover:bg-slate-50">
                <td className="px-4 py-3 text-slate-500">{u.id}</td>
                <td className="px-4 py-3 font-medium text-slate-800">{u.nickname}</td>
                <td className="px-4 py-3 text-slate-600">{u.phone || "-"}</td>
                <td className="px-4 py-3 text-slate-600">{u.email || "-"}</td>
                <td className="px-4 py-3">
                  <Badge className={cn("border-0", ROLE_BADGE[u.role] || ROLE_BADGE.USER)}>{u.role}</Badge>
                </td>
                <td className="px-4 py-3">
                  <Badge className={cn("border-0", STATUS_BADGE[u.status] || STATUS_BADGE.ACTIVE)}>{u.status}</Badge>
                </td>
                <td className="px-4 py-3 text-slate-500">{u.createdAt ? new Date(u.createdAt).toLocaleString() : "-"}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variant="outline"
                      className="h-7 px-2 text-xs"
                      disabled={busyId === u.id}
                      onClick={() =>
                        run(u.id, () =>
                          adminService.updateUserStatus(
                            tokens!.accessToken,
                            u.id,
                            u.status === "BANNED" ? "ACTIVE" : "BANNED",
                          ),
                        )
                      }
                    >
                      {u.status === "BANNED" ? "解封" : "封禁"}
                    </Button>
                    {isSuperAdmin && (
                      <>
                        <Button
                          variant="outline"
                          className="h-7 px-2 text-xs"
                          disabled={busyId === u.id}
                          onClick={() => openRoleModal(u)}
                        >
                          改角色
                        </Button>
                        <Button
                          variant="outline"
                          className="h-7 px-2 text-xs"
                          disabled={busyId === u.id}
                          onClick={() => handleResetPassword(u.id)}
                        >
                          重置密码
                        </Button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 分页 */}
      {data && (
        <div className="flex items-center justify-between text-sm text-slate-500">
          <span>共 {data.total} 条</span>
          <div className="flex gap-2">
            <Button variant="outline" className="h-8" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>上一页</Button>
            <span className="flex h-8 items-center px-2">{page} / {totalPages}</span>
            <Button variant="outline" className="h-8" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>下一页</Button>
          </div>
        </div>
      )}

      {/* 修改角色弹窗（单选） */}
      {roleTarget && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
          onClick={() => setRoleTarget(null)}
        >
          <div
            className="w-full max-w-sm rounded-xl bg-white p-5 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="text-sm font-semibold text-slate-800">
              修改角色 · <span className="text-slate-600">{roleTarget.nickname}</span>
            </div>
            <div className="mt-4 space-y-2">
              {ROLE_OPTIONS.map((opt) => {
                const active = selectedRole === opt.value
                return (
                  <label
                    key={opt.value}
                    className={cn(
                      "flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition-colors",
                      active ? "border-violet-500 bg-violet-50" : "border-slate-200 hover:border-slate-300",
                    )}
                  >
                    <input
                      type="radio"
                      name="role"
                      checked={active}
                      onChange={() => setSelectedRole(opt.value)}
                      className="size-4 accent-violet-600"
                    />
                    <div>
                      <div className="text-sm font-medium text-slate-800">{opt.label}</div>
                      <div className="text-xs text-slate-500">{opt.desc}</div>
                    </div>
                  </label>
                )
              })}
            </div>
            <div className="mt-5 flex justify-end gap-2">
              <Button variant="outline" className="h-9" onClick={() => setRoleTarget(null)}>取消</Button>
              <Button
                className="h-9 bg-slate-900 text-white hover:bg-slate-800"
                disabled={roleSaving || selectedRole === roleTarget.role}
                onClick={confirmRoleChange}
              >
                {roleSaving ? "保存中..." : "确认修改"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
