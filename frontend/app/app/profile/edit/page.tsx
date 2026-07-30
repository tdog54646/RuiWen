"use client"

import { useEffect, useMemo, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { TagInput } from "@/components/ui/tag-input"
import { UserAvatar } from "@/components/ui/user-avatar"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  GlassCard,
  MessageBanner,
  PageHeader,
  SectionLabel,
  StatusChip,
  StudioShell,
  glassInputClass,
} from "@/components/ui/studio"
import { useAuth } from "@/components/auth/auth-context"
import { profileService } from "@/lib/api/profile"
import { authService } from "@/lib/api/auth"
import type { Gender } from "@/lib/types/auth"
import type { ProfileUpdateRequest } from "@/lib/types/profile"
import { Camera, Loader2, Save, ArrowLeft } from "lucide-react"
import { cn } from "@/lib/utils"

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
    <StudioShell>
      <PageHeader
        title="编辑个人资料"
        subtitle="完善信息，帮助同学们更快认识你"
        badge={
          isSaving ? (
            <StatusChip icon={Loader2} tone="violet">
              保存中
            </StatusChip>
          ) : (
            <StatusChip tone="cyan">资料设置</StatusChip>
          )
        }
      />

      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
          {/* 头像 */}
          <GlassCard
            delay={0.05}
            className="lg:col-span-4"
            contentClassName="flex flex-col items-center gap-4"
          >
            <SectionLabel>头像</SectionLabel>
            <div
              className="group relative size-32 cursor-pointer overflow-hidden rounded-2xl ring-2 ring-white/60"
              onClick={() => fileInputRef.current?.click()}
            >
              <UserAvatar
                src={avatarUrl || undefined}
                nickname={displayName}
                className="size-32 text-3xl"
              />
              <div className="absolute inset-0 flex items-center justify-center bg-black/30 opacity-0 transition-opacity group-hover:opacity-100">
                {uploading ? (
                  <Loader2 className="size-6 animate-spin text-white" />
                ) : (
                  <Camera className="size-6 text-white" />
                )}
              </div>
            </div>
            <p className="text-center text-xs text-slate-400">
              点击更换头像
              <br />
              上传后自动保存
            </p>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={onAvatarFileChange}
            />
          </GlassCard>

          {/* 字段 */}
          <GlassCard
            delay={0.1}
            disableHover
            className="lg:col-span-8"
            contentClassName="flex flex-col gap-4"
          >
            <SectionLabel>基本信息</SectionLabel>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="nickname" className="text-xs text-slate-500">
                  昵称
                </Label>
                <Input
                  id="nickname"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  placeholder="填写你的昵称"
                  className={glassInputClass}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="phone" className="text-xs text-slate-500">
                  手机
                </Label>
                <Input
                  id="phone"
                  value={phone}
                  readOnly
                  placeholder="未绑定手机号"
                  className={cn(glassInputClass, "opacity-60")}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="email" className="text-xs text-slate-500">
                  邮箱
                </Label>
                <Input
                  id="email"
                  type="email"
                  value={email}
                  readOnly
                  placeholder="未绑定邮箱"
                  className={cn(glassInputClass, "opacity-60")}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="lineId" className="text-xs text-slate-500">
                  Line ID
                </Label>
                <Input
                  id="lineId"
                  value={lineId}
                  onChange={(e) => setLineId(e.target.value)}
                  placeholder="用于个性化主页地址"
                  className={glassInputClass}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="gender" className="text-xs text-slate-500">
                  性别
                </Label>
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
                  className={glassInputClass}
                />
                {genderError && (
                  <span className="text-xs text-destructive">{genderError}</span>
                )}
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="birthday" className="text-xs text-slate-500">
                  生日
                </Label>
                <Input
                  id="birthday"
                  type="date"
                  value={birthday}
                  onChange={(e) => setBirthday(e.target.value)}
                  className={glassInputClass}
                />
              </div>
              <div className="flex flex-col gap-1.5 sm:col-span-2">
                <Label htmlFor="school" className="text-xs text-slate-500">
                  学校 / 机构
                </Label>
                <Input
                  id="school"
                  value={school}
                  onChange={(e) => setSchool(e.target.value)}
                  placeholder="填写学校或机构"
                  className={glassInputClass}
                />
              </div>
              <div className="flex flex-col gap-1.5 sm:col-span-2">
                <Label className="text-xs text-slate-500">擅长领域</Label>
                <TagInput
                  value={skills}
                  onChange={setSkills}
                  placeholder="输入标签后按回车"
                />
              </div>
              <div className="flex flex-col gap-1.5 sm:col-span-2">
                <Label htmlFor="bio" className="text-xs text-slate-500">
                  个人简介
                </Label>
                <textarea
                  id="bio"
                  className="min-h-[120px] w-full resize-y rounded-xl border border-white/60 bg-white/50 p-3 text-sm outline-none transition-colors placeholder:text-slate-400 focus:border-cyan-400/60 focus:bg-white/70"
                  value={bio}
                  onChange={(e) => setBio(e.target.value)}
                  placeholder="介绍一下自己..."
                />
              </div>
            </div>
          </GlassCard>
        </div>

        <MessageBanner tone="error" show={!!saveError}>
          {saveError}
        </MessageBanner>
        <MessageBanner tone="success" show={!!saveMessage}>
          {saveMessage}
        </MessageBanner>

        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => router.push("/app/profile")}
            className="gap-1.5 border-white/60 bg-white/60 backdrop-blur-md"
          >
            <ArrowLeft className="size-3.5" />
            返回
          </Button>
          <Button
            type="submit"
            disabled={isSaving}
            className="gap-1.5 bg-gradient-to-r from-cyan-500 to-violet-600 text-white"
          >
            {isSaving ? (
              <Loader2 className="size-3.5 animate-spin" />
            ) : (
              <Save className="size-3.5" />
            )}
            {isSaving ? "保存中..." : "保存修改"}
          </Button>
        </div>
      </form>
    </StudioShell>
  )
}
