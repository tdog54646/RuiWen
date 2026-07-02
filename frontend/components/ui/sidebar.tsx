"use client"

import { AnimatePresence, motion } from "framer-motion"
import { Menu, X } from "lucide-react"
import { useMemo, useState } from "react"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

const DEFAULT_COLLAPSED_WIDTH = 88
const DEFAULT_EXPANDED_WIDTH = 292

type SidebarRenderProps = {
  expanded: boolean
  closeMobile: () => void
}

type SidebarProps = {
  className?: string
  collapsedWidth?: number
  expandedWidth?: number
  children: (props: SidebarRenderProps) => React.ReactNode
}

export function Sidebar({
  className,
  collapsedWidth = DEFAULT_COLLAPSED_WIDTH,
  expandedWidth = DEFAULT_EXPANDED_WIDTH,
  children,
}: SidebarProps) {
  const [isMobileOpen, setIsMobileOpen] = useState(false)
  const [isExpanded, setIsExpanded] = useState(false)

  const content = useMemo(
    () =>
      children({
        expanded: isExpanded || isMobileOpen,
        closeMobile: () => setIsMobileOpen(false),
      }),
    [children, isExpanded, isMobileOpen],
  )

  return (
    <>
      <Button
        variant="outline"
        size="icon"
        className="fixed top-4 left-4 z-50 rounded-xl md:hidden"
        onClick={() => setIsMobileOpen(true)}
        aria-label="打开侧边栏"
      >
        <Menu className="size-4" />
      </Button>

      <motion.aside
        className={cn(
          "sticky top-6 hidden h-[calc(100dvh-3rem)] shrink-0 overflow-hidden rounded-2xl border border-sidebar-border bg-sidebar-elevated text-sidebar-foreground shadow-sm md:flex",
          className,
        )}
        initial={false}
        animate={{ width: isExpanded ? expandedWidth : collapsedWidth }}
        transition={{ type: "spring", damping: 26, stiffness: 240, mass: 0.8 }}
        onMouseEnter={() => setIsExpanded(true)}
        onMouseLeave={() => setIsExpanded(false)}
      >
        {content}
      </motion.aside>

      <AnimatePresence>
        {isMobileOpen && (
          <>
            <motion.button
              type="button"
              className="fixed inset-0 z-40 bg-black/40 backdrop-blur-[1px] md:hidden"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setIsMobileOpen(false)}
              aria-label="关闭侧边栏遮罩"
            />
            <motion.aside
              className={cn(
                "fixed inset-y-0 left-0 z-50 flex w-[84vw] max-w-80 overflow-hidden border-r border-sidebar-border bg-sidebar-elevated text-sidebar-foreground shadow-xl md:hidden",
                className,
              )}
              initial={{ x: "-100%" }}
              animate={{ x: 0 }}
              exit={{ x: "-100%" }}
              transition={{ type: "spring", damping: 28, stiffness: 280 }}
            >
              <Button
                variant="ghost"
                size="icon"
                className="absolute top-4 right-4 z-10 rounded-xl"
                onClick={() => setIsMobileOpen(false)}
                aria-label="关闭侧边栏"
              >
                <X className="size-4" />
              </Button>
              {content}
            </motion.aside>
          </>
        )}
      </AnimatePresence>
    </>
  )
}
