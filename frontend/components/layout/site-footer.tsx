import Link from "next/link"
import { cn } from "@/lib/utils"

const ICP_URL = "https://beian.miit.gov.cn/"

export function SiteFooter({
  className,
  align = "center",
  showLegalLinks = true,
}: {
  className?: string
  align?: "start" | "center"
  showLegalLinks?: boolean
}) {
  return (
    <footer
      className={cn(
        "relative z-[1] flex flex-col gap-2 text-xs leading-5 text-[#747873]",
        align === "start" ? "items-start text-left" : "items-center text-center",
        className,
      )}
    >
      {showLegalLinks && (
        <nav aria-label="法律与合规" className="flex flex-wrap items-center gap-x-4 gap-y-1">
          <Link
            href="/terms"
            className="rounded-sm transition-colors hover:text-[#2f5d50] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
          >
            用户协议
          </Link>
          <Link
            href="/privacy"
            className="rounded-sm transition-colors hover:text-[#2f5d50] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
          >
            隐私政策
          </Link>
        </nav>
      )}
      <a
        href={ICP_URL}
        target="_blank"
        rel="noreferrer"
        className="rounded-sm transition-colors hover:text-[#2f5d50] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2f5d50]"
      >
        蒙ICP备2025024604号
      </a>
    </footer>
  )
}
