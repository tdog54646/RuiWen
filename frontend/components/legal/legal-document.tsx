import type { ReactNode } from "react"
import Link from "next/link"
import { ArrowLeft } from "lucide-react"
import { LineLogo } from "@/components/brand/line-logo"
import { SiteFooter } from "@/components/layout/site-footer"
import { cn } from "@/lib/utils"

export type LegalTocItem = {
  id: string
  title: string
}

export function LegalDocument({
  title,
  summary,
  effectiveDate,
  updatedDate,
  currentPath,
  toc,
  children,
}: {
  title: string
  summary: string
  effectiveDate: string
  updatedDate: string
  currentPath: "/terms" | "/privacy"
  toc: LegalTocItem[]
  children: ReactNode
}) {
  return (
    <div className="app-canvas relative min-h-dvh text-[#1d211f]">
      <a href="#legal-content" className="skip-link">
        跳到协议正文
      </a>

      <header className="sticky top-0 z-20 border-b border-[#deded8] bg-[#f7f7f3]/95 backdrop-blur-md">
        <div className="mx-auto flex h-16 w-full max-w-[1180px] items-center justify-between gap-4 px-5 sm:px-8">
          <Link
            href="/app"
            aria-label="返回 Line 首页"
            className="flex shrink-0 items-center gap-2.5 rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
          >
            <LineLogo variant="mark" className="size-8" priority />
            <span className="font-display text-lg font-semibold tracking-[-0.04em]">Line</span>
          </Link>

          <nav aria-label="法律文件" className="hidden items-center gap-6 text-sm text-[#626762] sm:flex">
            <Link
              href="/terms"
              aria-current={currentPath === "/terms" ? "page" : undefined}
              className={cn(
                "rounded-sm py-1 transition-colors hover:text-[#1d211f] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]",
                currentPath === "/terms" && "font-semibold text-[#2f5d50]",
              )}
            >
              用户协议
            </Link>
            <Link
              href="/privacy"
              aria-current={currentPath === "/privacy" ? "page" : undefined}
              className={cn(
                "rounded-sm py-1 transition-colors hover:text-[#1d211f] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]",
                currentPath === "/privacy" && "font-semibold text-[#2f5d50]",
              )}
            >
              隐私政策
            </Link>
          </nav>

          <Link
            href="/register"
            className="inline-flex shrink-0 items-center gap-1.5 rounded-sm text-sm font-semibold text-[#2f5d50] transition-colors hover:text-[#1d211f] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
          >
            <ArrowLeft className="size-4" aria-hidden="true" />
            返回注册
          </Link>
        </div>
      </header>

      <main id="legal-content" className="relative z-[1] mx-auto w-full max-w-[1180px] px-5 py-12 sm:px-8 sm:py-16 lg:py-20">
        <header className="max-w-3xl">
          <p className="text-sm font-semibold text-[#2f5d50]">Line 法律文件</p>
          <h1 className="mt-4 font-display text-4xl font-medium leading-[1.12] tracking-[-0.05em] sm:text-5xl">
            {title}
          </h1>
          <p className="mt-6 max-w-2xl text-[15px] leading-7 text-[#626762] sm:text-base">
            {summary}
          </p>
          <dl className="mt-8 flex flex-wrap gap-x-8 gap-y-3 text-sm text-[#747873]">
            <div className="flex gap-2">
              <dt>生效日期</dt>
              <dd className="font-medium text-[#3c433f]">{effectiveDate}</dd>
            </div>
            <div className="flex gap-2">
              <dt>更新日期</dt>
              <dd className="font-medium text-[#3c433f]">{updatedDate}</dd>
            </div>
          </dl>
        </header>

        <details className="mt-10 rounded-xl border border-[#deded8] bg-[#fbfbf8] p-4 lg:hidden">
          <summary className="cursor-pointer text-sm font-semibold text-[#2f5d50]">查看目录</summary>
          <nav aria-label={`${title}目录`} className="mt-4 grid gap-2 border-t border-[#deded8] pt-4 text-sm">
            {toc.map((item) => (
              <a
                key={item.id}
                href={`#${item.id}`}
                className="rounded-md px-2 py-1.5 text-[#626762] transition-colors hover:bg-[#ecece6] hover:text-[#1d211f] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
              >
                {item.title}
              </a>
            ))}
          </nav>
        </details>

        <div className="mt-12 grid items-start gap-12 lg:grid-cols-[15rem_minmax(0,1fr)] lg:gap-16">
          <aside className="sticky top-28 hidden lg:block">
            <p className="text-sm font-semibold text-[#3c433f]">目录</p>
            <nav aria-label={`${title}目录`} className="mt-4 flex flex-col gap-1 border-l border-[#d8d9d2] pl-4 text-sm">
              {toc.map((item) => (
                <a
                  key={item.id}
                  href={`#${item.id}`}
                  className="rounded-sm py-1.5 leading-5 text-[#747873] transition-colors hover:text-[#2f5d50] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
                >
                  {item.title}
                </a>
              ))}
            </nav>
          </aside>

          <article className="min-w-0 max-w-[760px]">{children}</article>
        </div>
      </main>

      <div className="relative z-[1] mx-auto w-full max-w-[1180px] border-t border-[#deded8] px-5 py-8 sm:px-8">
        <SiteFooter />
      </div>
    </div>
  )
}

export function LegalSection({
  id,
  title,
  children,
  className,
}: {
  id: string
  title: string
  children: ReactNode
  className?: string
}) {
  return (
    <section
      id={id}
      className={cn(
        "scroll-mt-28 border-t border-[#deded8] py-10 first:border-t-0 first:pt-0",
        className,
      )}
    >
      <h2 className="font-display text-2xl font-semibold leading-tight tracking-[-0.035em] text-[#252b28] sm:text-3xl">
        {title}
      </h2>
      <div className="mt-5 space-y-4 text-[15px] leading-7 text-[#555c57]">{children}</div>
    </section>
  )
}

export function LegalList({ children }: { children: ReactNode }) {
  return <ul className="space-y-2.5 pl-5 marker:text-[#6f8b79] [&>li]:list-disc">{children}</ul>
}

export function LegalNote({ children }: { children: ReactNode }) {
  return (
    <div className="border-l-2 border-[#6f8b79] bg-[#eef2ed] px-5 py-4 text-[#3f4b44]">
      {children}
    </div>
  )
}
