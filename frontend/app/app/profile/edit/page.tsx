"use client"

import { useEffect, useMemo, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { TagInput } from "@/components/ui/tag-input"
import { UserAvatar } from "@/components/ui/user-avatar"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useAuth } from "@/components/auth/auth-context"
import { profileService } from "@/lib/api/profile"
import { authService } from "@/lib/api/auth"
import type { Gender } from "@/lib/types/auth"
import type { ProfileUpdateRequest } from "@/lib/types/profile"
import { Camera, Loader2 } from "lucide-react"

export default function EditProfilePage() {
  const { user, tokens, reloadUser } = useAuth()
  const router = useRouter()
  const displayName = useMemo(
    () => user?.nickname ?? user?.phone ?? user?.email ?? "用户",
    [user],
  )

  const [nickname, setNickname] = useState("")
  const [bio, setBio] = useState("")
  const [lineId, setLineId] = useState("")
  const [genderText, setGenderText] = useState("")
  const [genderError, setGenderError] = useState("")
  const [birthday, setBirthday] = useState("")
  const [school, setSchool] = useState("")
  const [phone, setPhone] = useState("")
  const [email, setEmail] = useState("")
  const [skills, setSkills] = useState<string[]>([])
  const [isSaving, setIsSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState("")
  const [saveError, setSaveError] = useState("")
  const [uploading, setUploading] = useState(false)
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    let cancelled = false
    const run = async () => {
      if (!tokens?.accessToken) return
      try {
        const current = await authService.fetchCurrentUser(tokens.accessToken)
        if (cancelled) return
        setNickname(current.nickname ?? "")
        setBio(current.bio ?? "")
        setLineId(current.lineId ?? "")
        setPhone(current.phone ?? "")
        setEmail(current.email ?? "")
        setSchool(current.school ?? "")
        setBirthday(current.birthday ?? "")
        setAvatarUrl(current.avatar || null)
        if (current.gender === "MALE") setGenderText("男")
        else if (current.gender === "FEMALE") setGenderText("女")
        else setGenderText("")
        if (Array.isArray(current.skills)) {
          setSkills(current.skills)
        } else if (typeof current.tagJson === "string") {
          try {
            const parsed = JSON.parse(current.tagJson)
            if (Array.isArray(parsed)) setSkills(parsed.filter((x) => typeof x === "string"))
          } catch {}
        }
      } catch {}
    }
    void run()
    return () => {
      cancelled = true
    }
  }, [tokens?.accessToken])

  const onAvatarFileChange = async (
    e: React.ChangeEvent<HTMLInputElement>,
  ) => {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    setSaveMessage("")
    setSaveError("")
    try {
      const result = await profileService.uploadAvatar(file)
      setAvatarUrl(result.avatar || null)
      setSaveMessage("头像已更新")
      try {
        await reloadUser()
      } catch {}
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : "头像上传失败，请稍后重试")
    } finally {
      setUploading(false)
    }
  }

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsSaving(true)
    setSaveMessage("")
    setSaveError("")
    const payload: ProfileUpdateRequest = {}
    if (nickname.trim()) payload.nickname = nickname.trim()
    if (bio.trim()) payload.bio = bio.trim()
    if (lineId.trim()) payload.lineId = lineId.trim()
    const genderNormalized: Gender | undefined =
      genderText === "男" ? "MALE" : genderText === "女" ? "FEMALE" : undefined
    if (genderNormalized) payload.gender = genderNormalized
    if (birthday.trim()) payload.birthday = birthday.trim()
    if (school.trim()) payload.school = school.trim()
    if (email.trim()) payload.email = email.trim()
    if (skills.length > 0) payload.tagJson = JSON.stringify(skills)

    try {
      await profileService.update(payload)
      setSaveMessage("资料已保存")
      try {
        await reloadUser()
      } catch {}
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : "保存失败，请稍后重试")
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <form
      className="flex flex-col gap-6 rounded-2xl bg-background/90 p-6 shadow-sm"
      onSubmit={onSubmit}
    >
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight">编辑个人资料</h1>
        <p className="text-sm text-muted-foreground">完善信息，帮助同学们更快认识你</p>
      </div>

      <div className="flex flex-col gap-6">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">基本信息</h2>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => router.push("/app/profile")}
            >
              返回
            </Button>
            <Button type="submit" size="sm" disabled={isSaving}>
              {isSaving ? "保存中..." : "保存修改"}
            </Button>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-[200px_1fr]">
          <div className="flex flex-col items-start gap-3">
            <div
              className="relative size-32 cursor-pointer overflow-hidden rounded-2xl"
              onClick={() => fileInputRef.current?.click()}
            >
              <UserAvatar
                src={avatarUrl || undefined}
                nickname={displayName}
                size="lg"
                className="size-32 text-3xl"
              />
              <div className="absolute inset-0 flex items-center justify-center bg-black/20 opacity-0 transition-opacity hover:opacity-100">
                {uploading ? (
                  <Loader2 className="size-6 animate-spin text-white" />
                ) : (
                  <Camera className="size-6 text-white" />
                )}
              </div>
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={onAvatarFileChange}
            />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="nickname">昵称</Label>
              <Input
                id="nickname"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                placeholder="填写你的昵称"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="phone">手机</Label>
              <Input
                id="phone"
                value={phone}
                readOnly
                className="bg-muted/40 text-muted-foreground"
                placeholder="未绑定手机号"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="email">邮箱</Label>
              <Input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="填写邮箱地址"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="lineId">Line ID</Label>
              <Input
                id="lineId"
                value={lineId}
                onChange={(e) => setLineId(e.target.value)}
                placeholder="用于个性化主页地址"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="gender">性别</Label>
              <Input
                id="gender"
                value={genderText}
                onChange={(e) => {
                  const val = e.target.value.trim()
                  setGenderText(val)
                  if (!val || val === "男" || val === "女") setGenderError("")
                  else setGenderError("性别仅支持「男」或「女」")
                }}
                placeholder="请输入 男 或 女"
              />
              {genderError && (
                <span className="text-xs text-destructive">
                  {genderError}
                </span>
              )}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="birthday">生日</Label>
              <Input
                id="birthday"
                type="date"
                value={birthday}
                onChange={(e) => setBirthday(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="school">学校/机构</Label>
              <Input
                id="school"
                value={school}
                onChange={(e) => setSchool(e.target.value)}
                placeholder="填写学校或机构"
              />
            </div>
            <div className="space-y-1.5 sm:col-span-2">
              <Label>擅长领域</Label>
              <TagInput
                value={skills}
                onChange={setSkills}
                placeholder="输入标签后按回车"
              />
            </div>
            <div className="space-y-1.5 sm:col-span-2">
              <Label htmlFor="bio">个人简介</Label>
              <textarea
                id="bio"
                className="min-h-[120px] w-full resize-y rounded-lg border bg-background p-3 text-sm outline-none focus:border-ring"
                value={bio}
                onChange={(e) => setBio(e.target.value)}
                placeholder="介绍一下自己..."
              />
            </div>
          </div>
        </div>

        {saveError && (
          <div className="rounded-lg border border-red-900/30 bg-red-950/20 p-3 text-sm text-red-400">
            {saveError}
          </div>
        )}
        {saveMessage && (
          <div className="text-sm font-medium text-primary">
            {saveMessage}
          </div>
        )}
      </div>
    </form>
  )
}
