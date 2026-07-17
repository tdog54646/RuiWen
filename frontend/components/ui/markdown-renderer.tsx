"use client"

import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import rehypeHighlight from "rehype-highlight"
import "highlight.js/styles/github.css"
import { cn } from "@/lib/utils"

type MarkdownRendererProps = {
  content: string
  className?: string
}

export function MarkdownRenderer({ content, className }: MarkdownRendererProps) {
  return (
    <div
      className={cn(
        "prose prose-neutral dark:prose-invert max-w-none",
        "prose-headings:font-semibold prose-headings:tracking-tight",
        "prose-a:text-primary prose-a:no-underline hover:prose-a:underline",
        "prose-code:before:content-none prose-code:after:content-none",
        "prose-code:rounded prose-code:bg-muted prose-code:px-1.5 prose-code:py-0.5 prose-code:text-sm",
        "prose-pre:bg-muted prose-pre:border prose-pre:border-border",
        "prose-img:rounded-lg",
        className
      )}
    >
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        // detect: 无语言标识的代码块自动检测语言并补 hljs 类，
        // 否则会退回 prose 默认浅色字、落在浅灰 pre 底上对比度极低（偏暗）
        rehypePlugins={[[rehypeHighlight, { detect: true }]]}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}
