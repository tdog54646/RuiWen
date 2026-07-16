import Image from "next/image"

import { cn } from "@/lib/utils"

type LineLogoProps = {
  variant?: "lockup" | "mark"
  className?: string
  priority?: boolean
}

export function LineLogo({
  variant = "lockup",
  className,
  priority = false,
}: LineLogoProps) {
  const isMark = variant === "mark"

  return (
    <Image
      src={
        isMark
          ? "/brand/line-icon-transparent.png"
          : "/brand/line-logo-transparent.png"
      }
      alt={isMark ? "Line 标志" : "Line"}
      width={isMark ? 512 : 800}
      height={isMark ? 512 : 325}
      priority={priority}
      className={cn(
        "select-none object-contain",
        isMark ? "aspect-square" : "h-auto",
        className,
      )}
    />
  )
}
