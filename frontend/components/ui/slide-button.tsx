"use client"

import React, {
  forwardRef,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type JSX,
} from "react"
import {
  AnimatePresence,
  motion,
  useMotionValue,
  useSpring,
  useTransform,
  type PanInfo,
} from "framer-motion"
import { Check, Loader2, SendHorizontal, X } from "lucide-react"

import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"

const DRAG_CONSTRAINTS = { left: 0, right: 155 }
const DRAG_THRESHOLD = 0.9

const BUTTON_STATES = {
  initial: { width: "12rem" },
  completed: { width: "8rem" },
}

const ANIMATION_CONFIG = {
  spring: {
    type: "spring",
    stiffness: 400,
    damping: 40,
    mass: 0.8,
  },
} as const

type ButtonStatus = "idle" | "loading" | "success" | "error"

type StatusIconProps = {
  status: ButtonStatus
}

type SlideButtonProps = React.ComponentProps<typeof Button> & {
  onSlideComplete?: () => Promise<void> | void
  resetOnErrorMs?: number
  resetOnSuccessMs?: number
  idleText?: string
  loadingText?: string
  successText?: string
  errorText?: string
}

const StatusIcon: React.FC<StatusIconProps> = ({ status }) => {
  const iconMap: Record<Exclude<ButtonStatus, "idle">, JSX.Element> = useMemo(
    () => ({
      loading: <Loader2 className="animate-spin" size={20} />,
      success: <Check size={20} />,
      error: <X size={20} />,
    }),
    []
  )

  if (status === "idle") return null

  return (
    <motion.div
      key={status}
      initial={{ opacity: 0, scale: 0.5 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0 }}
    >
      {iconMap[status]}
    </motion.div>
  )
}

const SlideButton = forwardRef<HTMLButtonElement, SlideButtonProps>(
  (
    {
      className,
      onSlideComplete,
      resetOnErrorMs = 1600,
      resetOnSuccessMs = 1400,
      idleText = "滑动发布",
      loadingText = "发布中",
      successText = "已发布",
      errorText = "发布失败",
      disabled,
      ...props
    },
    ref
  ) => {
    const [isDragging, setIsDragging] = useState(false)
    const [completed, setCompleted] = useState(false)
    const [status, setStatus] = useState<ButtonStatus>("idle")
    const resetTimerRef = useRef<number | null>(null)

    const dragX = useMotionValue(0)
    const springX = useSpring(dragX, ANIMATION_CONFIG.spring)
    const dragProgress = useTransform(
      springX,
      [0, DRAG_CONSTRAINTS.right],
      [0, 1]
    )

    const clearResetTimer = useCallback(() => {
      if (resetTimerRef.current) {
        window.clearTimeout(resetTimerRef.current)
        resetTimerRef.current = null
      }
    }, [])

    const resetToIdle = useCallback(() => {
      clearResetTimer()
      setCompleted(false)
      setStatus("idle")
      dragX.set(0)
    }, [clearResetTimer, dragX])

    const scheduleReset = useCallback(
      (delayMs: number) => {
        if (delayMs <= 0) return
        clearResetTimer()
        resetTimerRef.current = window.setTimeout(() => {
          resetToIdle()
        }, delayMs)
      },
      [clearResetTimer, resetToIdle]
    )

    const handleSubmit = useCallback(async () => {
      clearResetTimer()
      setStatus("loading")
      try {
        if (onSlideComplete) {
          await onSlideComplete()
        } else {
          await new Promise((resolve) => setTimeout(resolve, 1200))
        }
        setStatus("success")
        scheduleReset(resetOnSuccessMs)
      } catch {
        setStatus("error")
        scheduleReset(resetOnErrorMs)
      }
    }, [
      clearResetTimer,
      onSlideComplete,
      resetOnErrorMs,
      resetOnSuccessMs,
      scheduleReset,
    ])

    useEffect(() => {
      return () => clearResetTimer()
    }, [clearResetTimer])

    const handleDragStart = useCallback(() => {
      if (completed || disabled) return
      setIsDragging(true)
    }, [completed, disabled])

    const handleDragEnd = () => {
      if (completed || disabled) return
      setIsDragging(false)

      const progress = dragProgress.get()
      if (progress >= DRAG_THRESHOLD) {
        dragX.set(DRAG_CONSTRAINTS.right)
        setCompleted(true)
        void handleSubmit()
      } else {
        dragX.set(0)
      }
    }

    const handleDrag = (
      _event: MouseEvent | TouchEvent | PointerEvent,
      info: PanInfo
    ) => {
      if (completed || disabled) return
      const newX = Math.max(0, Math.min(info.offset.x, DRAG_CONSTRAINTS.right))
      dragX.set(newX)
    }

    const adjustedWidth = useTransform(springX, (x) => x + 10)
    const statusText = useMemo(() => {
      switch (status) {
        case "loading":
          return loadingText
        case "success":
          return successText
        case "error":
          return errorText
        default:
          return idleText
      }
    }, [errorText, idleText, loadingText, status, successText])

    return (
      <motion.div
        animate={completed ? BUTTON_STATES.completed : BUTTON_STATES.initial}
        transition={ANIMATION_CONFIG.spring}
        className="shadow-button-inset dark:shadow-button-inset-dark relative flex h-9 items-center justify-center rounded-full bg-gray-100"
      >
        {!completed && (
          <motion.div
            style={{ width: adjustedWidth }}
            className="absolute inset-y-0 left-0 z-0 rounded-full bg-accent"
          />
        )}
        {!completed && (
          <span className="pointer-events-none z-0 pl-7 text-xs font-medium text-muted-foreground">
            {idleText}
          </span>
        )}
        <AnimatePresence>
          {!completed && (
            <motion.div
              drag="x"
              dragConstraints={DRAG_CONSTRAINTS}
              dragElastic={0.05}
              dragMomentum={false}
              onDragStart={handleDragStart}
              onDragEnd={handleDragEnd}
              onDrag={handleDrag}
              style={{ x: springX }}
              className="absolute -left-4 z-10 flex cursor-grab items-center justify-start active:cursor-grabbing"
            >
              <Button
                ref={ref}
                disabled={disabled || status === "loading"}
                {...props}
                size="icon"
                className={cn(
                  "shadow-button rounded-full drop-shadow-xl",
                  isDragging && "scale-105 transition-transform",
                  className
                )}
              >
                <SendHorizontal className="size-4" />
              </Button>
            </motion.div>
          )}
        </AnimatePresence>

        <AnimatePresence>
          {completed && (
            <motion.div
              className="absolute inset-0 flex items-center justify-center"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
            >
              <Button
                ref={ref}
                disabled={disabled || status === "loading"}
                {...props}
                className={cn(
                  "size-full rounded-full transition-all duration-300",
                  className
                )}
              >
                <span className="inline-flex items-center gap-1.5 text-xs">
                  <AnimatePresence mode="wait">
                    <StatusIcon status={status} />
                  </AnimatePresence>
                  <span>{statusText}</span>
                </span>
              </Button>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>
    )
  }
)

SlideButton.displayName = "SlideButton"

export { SlideButton }
