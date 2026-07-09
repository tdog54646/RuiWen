"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import type { editor } from "monaco-editor";
import {
  Bold,
  Code,
  Code2,
  Edit3,
  Eye,
  Heading1,
  Heading2,
  Heading3,
  Image as ImageIcon,
  Italic,
  Link as LinkIcon,
  List,
  ListChecks,
  ListOrdered,
  Minus,
  Quote,
  Strikethrough,
  Table,
} from "lucide-react";
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

// 一键插入的 Markdown 文本（纯文本；选中段会由 insertInline 单独选中）
const MD_LINK = "[链接文本](https://)";
const MD_IMAGE = "![图片描述](https://)";
const MD_TABLE =
  "| 列1 | 列2 | 列3 |\n| --- | --- | --- |\n| 内容 | 内容 | 内容 |";
const MD_CODEBLOCK = "```js\n代码\n```";
const MD_HR = "---";

// 计算从 start 位置写入 text 后的光标位置（处理多行文本）
function posAfter(
  start: { lineNumber: number; column: number },
  text: string,
): { lineNumber: number; column: number } {
  const segs = text.split("\n");
  if (segs.length === 1) {
    return { lineNumber: start.lineNumber, column: start.column + segs[0].length };
  }
  return {
    lineNumber: start.lineNumber + segs.length - 1,
    column: segs[segs.length - 1].length + 1,
  };
}

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

  // —— Markdown 快捷插入：全部基于 Monaco executeEdits（不依赖 insertSnippet 命令）——
  const focusEditor = () => editorRef.current?.focus();

  // 行内包裹：有选区则包裹选区，无选区则插入占位符并选中它
  const wrapInline = useCallback(
    (before: string, after: string, placeholder = "文本") => {
      const inst = editorRef.current;
      const model = inst?.getModel();
      const sel = inst?.getSelection();
      if (!inst || !model || !sel) return;
      const selected = model.getValueInRange(sel);
      const inner = selected.length > 0 ? selected : placeholder;
      inst.pushUndoStop();
      inst.executeEdits("markdown-toolbar", [
        {
          range: sel,
          text: before + inner + after,
          forceMoveMarkers: true,
        },
      ]);
      inst.pushUndoStop();
      // 选中新插入的 inner 文本，方便直接输入覆盖
      const start = sel.getStartPosition();
      const innerStart = posAfter(start, before);
      const innerEnd = posAfter(innerStart, inner);
      inst.setSelection({
        startLineNumber: innerStart.lineNumber,
        startColumn: innerStart.column,
        endLineNumber: innerEnd.lineNumber,
        endColumn: innerEnd.column,
      });
      focusEditor();
    },
    [],
  );

  // 在光标处插入文本，可选选中其中一段（selectText）
  const insertInline = useCallback((text: string, selectText?: string) => {
    const inst = editorRef.current;
    const pos = inst?.getPosition();
    if (!inst || !pos) return;
    inst.pushUndoStop();
    inst.executeEdits("markdown-toolbar", [
      {
        range: {
          startLineNumber: pos.lineNumber,
          startColumn: pos.column,
          endLineNumber: pos.lineNumber,
          endColumn: pos.column,
        },
        text,
        forceMoveMarkers: true,
      },
    ]);
    inst.pushUndoStop();
    if (selectText) {
      const idx = text.indexOf(selectText);
      if (idx >= 0) {
        const start = posAfter(pos, text.slice(0, idx));
        const end = posAfter(start, selectText);
        inst.setSelection({
          startLineNumber: start.lineNumber,
          startColumn: start.column,
          endLineNumber: end.lineNumber,
          endColumn: end.column,
        });
      }
    }
    focusEditor();
  }, []);

  // 行首前缀切换（标题、无序列表、任务清单、引用等），支持多行
  const toggleLinePrefix = useCallback((prefix: string) => {
    const inst = editorRef.current;
    const model = inst?.getModel();
    const sel = inst?.getSelection();
    if (!inst || !model || !sel) return;
    const ops: editor.IIdentifiedSingleEditOperation[] = [];
    for (let l = sel.startLineNumber; l <= sel.endLineNumber; l++) {
      const content = model.getLineContent(l);
      const has = content.startsWith(prefix);
      ops.push({
        range: {
          startLineNumber: l,
          startColumn: 1,
          endLineNumber: l,
          endColumn: content.length + 1,
        },
        text: has ? content.slice(prefix.length) : `${prefix}${content}`,
        forceMoveMarkers: true,
      });
    }
    inst.pushUndoStop();
    inst.executeEdits("markdown-toolbar", ops);
    inst.pushUndoStop();
    focusEditor();
  }, []);

  // 有序列表：自动编号，再次点击移除
  const toggleOrderedList = useCallback(() => {
    const inst = editorRef.current;
    const model = inst?.getModel();
    const sel = inst?.getSelection();
    if (!inst || !model || !sel) return;
    const ops: editor.IIdentifiedSingleEditOperation[] = [];
    let n = 1;
    for (let l = sel.startLineNumber; l <= sel.endLineNumber; l++) {
      const content = model.getLineContent(l);
      const m = content.match(/^\d+\.\s+/);
      ops.push({
        range: {
          startLineNumber: l,
          startColumn: 1,
          endLineNumber: l,
          endColumn: content.length + 1,
        },
        text: m ? content.slice(m[0].length) : `${n}. ${content}`,
        forceMoveMarkers: true,
      });
      if (!m) n++;
    }
    inst.pushUndoStop();
    inst.executeEdits("markdown-toolbar", ops);
    inst.pushUndoStop();
    focusEditor();
  }, []);

  // 块级插入：自动补前后空行，保证独立成段
  const insertBlock = useCallback((block: string) => {
    const inst = editorRef.current;
    const model = inst?.getModel();
    const pos = inst?.getPosition();
    if (!inst || !model || !pos) return;
    const emptyLine = model.getLineContent(pos.lineNumber).trim() === "";
    const lead = emptyLine ? "" : "\n\n";
    inst.pushUndoStop();
    inst.executeEdits("markdown-toolbar", [
      {
        range: {
          startLineNumber: pos.lineNumber,
          startColumn: pos.column,
          endLineNumber: pos.lineNumber,
          endColumn: pos.column,
        },
        text: `${lead}${block}`,
        forceMoveMarkers: true,
      },
    ]);
    inst.pushUndoStop();
    focusEditor();
  }, []);

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

      {/* Markdown 快捷插入工具条 */}
      {mode !== "preview" && (
        <div className="no-scrollbar flex items-center gap-0.5 overflow-x-auto border-b border-white/50 bg-white/40 px-2 py-1 backdrop-blur-md dark:border-white/10 dark:bg-white/5">
          {/* 行内格式 */}
          <ToolButton title="加粗" onClick={() => wrapInline("**", "**", "加粗")}>
            <Bold className="size-3.5" />
          </ToolButton>
          <ToolButton title="斜体" onClick={() => wrapInline("*", "*", "斜体")}>
            <Italic className="size-3.5" />
          </ToolButton>
          <ToolButton
            title="删除线"
            onClick={() => wrapInline("~~", "~~", "删除线")}
          >
            <Strikethrough className="size-3.5" />
          </ToolButton>
          <ToolButton title="行内代码" onClick={() => wrapInline("`", "`", "code")}>
            <Code className="size-3.5" />
          </ToolButton>

          <Divider />

          {/* 标题 */}
          <ToolButton title="一级标题" onClick={() => toggleLinePrefix("# ")}>
            <Heading1 className="size-3.5" />
          </ToolButton>
          <ToolButton title="二级标题" onClick={() => toggleLinePrefix("## ")}>
            <Heading2 className="size-3.5" />
          </ToolButton>
          <ToolButton title="三级标题" onClick={() => toggleLinePrefix("### ")}>
            <Heading3 className="size-3.5" />
          </ToolButton>

          <Divider />

          {/* 列表 / 引用 */}
          <ToolButton title="无序列表" onClick={() => toggleLinePrefix("- ")}>
            <List className="size-3.5" />
          </ToolButton>
          <ToolButton title="有序列表" onClick={toggleOrderedList}>
            <ListOrdered className="size-3.5" />
          </ToolButton>
          <ToolButton title="任务清单" onClick={() => toggleLinePrefix("- [ ] ")}>
            <ListChecks className="size-3.5" />
          </ToolButton>
          <ToolButton title="引用" onClick={() => toggleLinePrefix("> ")}>
            <Quote className="size-3.5" />
          </ToolButton>

          <Divider />

          {/* 插入 */}
          <ToolButton title="链接" onClick={() => insertInline(MD_LINK, "链接文本")}>
            <LinkIcon className="size-3.5" />
          </ToolButton>
          <ToolButton title="图片" onClick={() => insertInline(MD_IMAGE, "图片描述")}>
            <ImageIcon className="size-3.5" />
          </ToolButton>
          <ToolButton title="表格" onClick={() => insertBlock(MD_TABLE)}>
            <Table className="size-3.5" />
          </ToolButton>
          <ToolButton title="代码块" onClick={() => insertBlock(MD_CODEBLOCK)}>
            <Code2 className="size-3.5" />
          </ToolButton>
          <ToolButton title="分割线" onClick={() => insertBlock(MD_HR)}>
            <Minus className="size-3.5" />
          </ToolButton>
        </div>
      )}

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

/* --------------------------- 工具条局部组件 --------------------------- */

function ToolButton({
  title,
  onClick,
  children,
}: {
  title: string;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      title={title}
      aria-label={title}
      // 阻止点击按钮时编辑器失焦 / 丢失当前选区
      onMouseDown={(e) => e.preventDefault()}
      onClick={onClick}
      className="flex size-7 shrink-0 items-center justify-center rounded-md text-slate-500 transition-all hover:bg-white/80 hover:text-slate-900 active:scale-90 dark:text-slate-400 dark:hover:bg-white/10 dark:hover:text-white"
    >
      {children}
    </button>
  );
}

function Divider() {
  return (
    <span
      aria-hidden
      className="mx-1 h-4 w-px shrink-0 bg-slate-300/60 dark:bg-white/10"
    />
  );
}
