"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import type { editor } from "monaco-editor";
import { Eye, Edit3 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { MarkdownRenderer } from "@/components/ui/markdown-renderer";
import { cn } from "@/lib/utils";

declare global {
  interface Window {
    monaco?: typeof import("monaco-editor");
    __monacoEditorInstance?: editor.IStandaloneCodeEditor;
  }
}

type EditorMode = "edit" | "split" | "preview";

interface AdvancedMarkdownEditorProps {
  initialValue?: string;
  onChange?: (value: string) => void;
}

const MONACO_BASE = "/monaco-editor/vs";
const MIN_EDITOR_HEIGHT = 360;

function loadMonaco(): Promise<typeof import("monaco-editor")> {
  return new Promise((resolve, reject) => {
    if (window.monaco) {
      resolve(window.monaco);
      return;
    }

    // If loader.js already injected, Monaco is loading — wait for it
    if (document.querySelector('script[data-monaco-loader]')) {
      const poll = setInterval(() => {
        if (window.monaco) {
          clearInterval(poll);
          resolve(window.monaco);
        }
      }, 100);
      return;
    }

    const script = document.createElement("script");
    script.dataset.monacoLoader = "true";
    script.src = `${MONACO_BASE}/loader.js`;
    script.onload = () => {
      const req = (window as typeof window & {
        require?: {
          (deps: string[], cb: () => void): void;
          config(options: { paths: Record<string, string> }): void;
        };
      }).require;
      if (!req) {
        reject(new Error("require not found after loader.js"));
        return;
      }
      // Configure AMD paths BEFORE requesting any module
      req.config({ paths: { vs: MONACO_BASE } });
      req(["vs/editor/editor.main"], () => {
        if (window.monaco) {
          resolve(window.monaco);
        } else {
          reject(new Error("window.monaco not set after editor.main loaded"));
        }
      });
    };
    script.onerror = () =>
      reject(new Error(`Failed to load ${MONACO_BASE}/loader.js`));
    document.head.appendChild(script);
  });
}

export default function AdvancedMarkdownEditor({
  initialValue = "",
  onChange,
}: AdvancedMarkdownEditorProps) {
  const editorContainerRef = useRef<HTMLDivElement>(null);
  const previewRef = useRef<HTMLDivElement>(null);
  const [mode, setMode] = useState<EditorMode>("split");
  const [isReady, setIsReady] = useState(false);
  const [isDark, setIsDark] = useState(
    () => document.documentElement.classList.contains("dark")
  );
  const [currentValue, setCurrentValue] = useState(initialValue);
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
  const isScrollingRef = useRef<"editor" | "preview" | null>(null);

  // 编辑区高度：autoHeight 随内容自适应；manualMin 为用户拖拽设定的下限
  const [autoHeight, setAutoHeight] = useState(600);
  const [manualMin, setManualMin] = useState(MIN_EDITOR_HEIGHT);
  const [maxHeight] = useState(() =>
    typeof window !== "undefined" ? Math.round(window.innerHeight * 0.8) : 1000
  );
  const effectiveHeight = Math.max(manualMin, Math.min(autoHeight, maxHeight));

  const syncAutoHeight = useCallback(() => {
    const inst = editorRef.current;
    if (!inst) return;
    const h = inst.getContentHeight();
    setAutoHeight((prev) => (Math.abs(prev - h) < 1 ? prev : h));
  }, []);

  const handleResizeStart = (e: React.PointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    const startY = e.clientY;
    const startH = effectiveHeight;
    const onMove = (ev: PointerEvent) => {
      const next = startH + (ev.clientY - startY);
      setManualMin(Math.max(MIN_EDITOR_HEIGHT, Math.min(next, maxHeight)));
    };
    const onUp = () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
  };

  useEffect(() => {
    const observer = new MutationObserver(() => {
      setIsDark(document.documentElement.classList.contains("dark"));
    });
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["class"],
    });
    return () => observer.disconnect();
  }, []);

  // Sync external initialValue changes into the editor
  useEffect(() => {
    if (!editorRef.current) return;
    const current = editorRef.current.getValue();
    if (current !== initialValue) {
      editorRef.current.setValue(initialValue ?? "");
    }
  }, [initialValue]);

  // Monaco editor initialization
  useEffect(() => {
    let ro: ResizeObserver | null = null;
    let cancelled = false;

    loadMonaco()
      .then((monaco) => {
        if (cancelled || !editorContainerRef.current) return;

        const container = editorContainerRef.current;
        const instance = monaco.editor.create(container, {
          value: initialValue,
          language: "markdown",
          theme: isDark ? "vs-dark" : "vs-light",
          automaticLayout: false,
          wordWrap: "on",
          minimap: { enabled: false },
          fontSize: 16,
          lineNumbers: "on",
          renderLineHighlight: "all",
          scrollBeyondLastLine: false,
          fontFamily: "var(--font-mono)",
          fontLigatures: true,
          smoothScrolling: true,
          cursorSmoothCaretAnimation: "on",
          autoClosingQuotes: "always",
          dragAndDrop: true,
          padding: { top: 16, bottom: 16 },
          overviewRulerLanes: 0,
          hideCursorInOverviewRuler: true,
          scrollbar: {
            verticalScrollbarSize: 8,
            horizontalScrollbarSize: 8,
          },
        });

        if (cancelled) {
          instance.dispose();
          return;
        }

        editorRef.current = instance;
        window.__monacoEditorInstance = instance;

        // Precise layout only when container size actually changes
        ro = new ResizeObserver(() => instance.layout());
        ro.observe(container);
        instance.onDidDispose(() => ro?.disconnect());

        monaco.languages.setLanguageConfiguration("markdown", {
          autoClosingPairs: [
            { open: "{", close: "}" },
            { open: "[", close: "]" },
            { open: "(", close: ")" },
            { open: '"', close: '"' },
            { open: "'", close: "'" },
            { open: "`", close: "`" },
          ],
        });

        monaco.languages.setMonarchTokensProvider("markdown", {
          tokenizer: {
            root: [
              [/\|\|/, "string"],
              [/>>/, "string"],
              [/\$\$[\s\S]+?\$\$/, "keyword"],
              [/\$\[[^\]]+\]/, "keyword"],
            ],
          },
        });

        instance.onDidChangeModelContent(() => {
          const val = instance.getValue();
          setCurrentValue(val);
          onChange?.(val);
        });

        instance.onDidScrollChange((e) => {
          if (isScrollingRef.current === "preview") return;
          isScrollingRef.current = "editor";
          const preview = previewRef.current;
          if (preview) {
            const total =
              instance.getScrollHeight() - instance.getLayoutInfo().height;
            if (total > 0) {
              const ratio = e.scrollTop / total;
              preview.scrollTop =
                ratio * (preview.scrollHeight - preview.clientHeight);
            }
          }
          isScrollingRef.current = null;
        });

        instance.onDidContentSizeChange(() => syncAutoHeight());

        setIsReady(true);
        instance.focus();
      })
      .catch((err) => {
        console.error("[Monaco] failed to load:", err);
      });

    return () => {
      cancelled = true;
      ro?.disconnect();
      editorRef.current?.dispose();
      editorRef.current = null;
      window.__monacoEditorInstance = undefined;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Theme sync
  useEffect(() => {
    if (!isReady || !editorRef.current) return;
    const monaco = window.monaco;
    if (monaco) {
      monaco.editor.setTheme(isDark ? "vs-dark" : "vs-light");
    }
  }, [isDark, isReady]);

  // Scroll sync: preview -> editor
  const handlePreviewScroll = useCallback(() => {
    if (isScrollingRef.current === "editor") return;
    const editor = editorRef.current;
    const preview = previewRef.current;
    if (!editor || !preview) return;
    isScrollingRef.current = "preview";
    const total = preview.scrollHeight - preview.clientHeight;
    if (total > 0) {
      const ratio = preview.scrollTop / total;
      const editorTotal =
        editor.getScrollHeight() - editor.getLayoutInfo().height;
      editor.setScrollTop(ratio * editorTotal);
    }
    isScrollingRef.current = null;
  }, []);

  const handleModeChange = useCallback(
    (m: EditorMode) => {
      setMode(m);
      setTimeout(() => {
        editorRef.current?.layout();
        syncAutoHeight();
      }, 60);
    },
    [syncAutoHeight]
  );

  return (
    <div className="flex flex-col overflow-hidden rounded-xl border border-white/50 shadow-sm dark:border-white/10">
      {/* Mode toolbar */}
      <div className="flex items-center gap-1 border-b border-white/50 bg-white/50 px-2 py-1.5 backdrop-blur-md dark:border-white/10 dark:bg-white/5">
        <Button
          variant={mode === "edit" ? "secondary" : "ghost"}
          size="xs"
          onClick={() => handleModeChange("edit")}
        >
          <Edit3 className="mr-1 size-3" />
          编辑
        </Button>
        <Button
          variant={mode === "split" ? "secondary" : "ghost"}
          size="xs"
          onClick={() => handleModeChange("split")}
        >
          左右分栏
        </Button>
        <Button
          variant={mode === "preview" ? "secondary" : "ghost"}
          size="xs"
          onClick={() => handleModeChange("preview")}
        >
          <Eye className="mr-1 size-3" />
          预览
        </Button>
        <div className="ml-auto text-xs text-muted-foreground px-2">
          {currentValue.length} 字
        </div>
      </div>

      {/* Editor area */}
      <div className="flex" style={{ height: effectiveHeight }}>
        {/* Monaco Editor — left pane */}
        <div
          className={cn(
            "overflow-hidden transition-all duration-200",
            mode === "preview" && "hidden",
            mode === "split" && "w-1/2 border-r",
            mode === "edit" && "flex-1"
          )}
        >
          {!isReady && (
            <div className="flex items-center justify-center h-full text-sm text-muted-foreground">
              编辑器加载中...
            </div>
          )}
          <div ref={editorContainerRef} className="w-full h-full" />
        </div>

        {/* Markdown Preview — right pane */}
        <div
          ref={previewRef}
          className={cn(
            "overflow-auto bg-background transition-all duration-200",
            mode === "edit" && "hidden",
            mode === "split" && "w-1/2",
            mode === "preview" && "flex-1"
          )}
          onScroll={handlePreviewScroll}
        >
          <div className="p-6">
            {currentValue ? (
              <MarkdownRenderer content={currentValue} />
            ) : (
              <p className="text-sm text-muted-foreground">
                开始输入内容，预览将实时显示...
              </p>
            )}
          </div>
        </div>
      </div>

      {/* 拖拽手柄：上下拖动调整编辑区高度，双击恢复随内容自适应 */}
      <div
        role="separator"
        aria-orientation="horizontal"
        onPointerDown={handleResizeStart}
        onDoubleClick={() => setManualMin(MIN_EDITOR_HEIGHT)}
        className="group/drag flex h-4 cursor-row-resize items-center justify-center border-t border-white/50 bg-white/40 transition-colors hover:bg-white/70 dark:border-white/10 dark:bg-white/5"
      >
        <span className="h-1 w-10 rounded-full bg-slate-300 transition-colors group-hover/drag:bg-violet-400" />
      </div>
    </div>
  );
}
