# Markdown 文档切分单步完成提示词（One-Shot）
# 用途：一次性完成 Markdown 文档的 结构提取 和 Chunk 合并，只需一次 LLM 调用即可得到可直接入库的 Chunk 列表。

## 输入

一段 Markdown 文本字符串（通过 user message 传入）。

## 配置参数

| 变量 | 类型 | 说明 | 默认值 |
|------|------|------|--------|
| `{{chunk_token_limit}}` | int | 每个 Chunk 最大 token 数 | 500 |
| `{{overlap_percent}}` | int | 相邻 Chunk 重叠比例（%），0 表示不重叠 | 0 |
| `{{min_chunk_tokens}}` | int | Chunk 最小 token 数（低于此值的碎片不输出） | 50 |
| `{{children_delimiters}}` | string | 子 Chunk 分隔符（如 "##,---,***"） | "" |

## 输出格式

输出**仅包含 JSON**，不要包含任何 Markdown 包裹或解释文字。

```json
{
  "chunks": [
    {
      "index": 0,
      "content": "### 什么是 SSO\n\n单点登录 (Single Sign-On，简称 SSO) 是一种身份验证机制...\n\n### SSO 是怎么实现的\n\n实现 SSO 的核心思路是**剥离各个子系统的登录逻辑**，建立一个独立的认证中心。",
      "source_section_types": ["header_with_text", "header_with_text"],
      "source_line_range": "1-10",
      "est_tokens": 127
    },
    {
      "index": 1,
      "content": "## 代码示例\n\n```python\ndef sso_auth():\n    return 'token'\n```",
      "source_section_types": ["header_with_code"],
      "source_line_range": "12-18",
      "est_tokens": 89
    },
    {
      "index": 2,
      "content": "| 协议 | 特点 |\n|-----|------|\n| SAML | 企业级 |\n| OAuth 2.0 | 互联网 |\n| OIDC | 现代标准 |",
      "source_section_types": ["table"],
      "source_line_range": "20-25",
      "est_tokens": 42
    }
  ]
}
```

## 处理流程

### Step 1：结构提取

按以下优先级识别块级元素（内部执行，不暴露）：

1. `code_block` — 三个反引号包裹，保留语言标识和完整内容
2. `table` — 以 `|` 分隔列的整体表格（不拆分行）
3. `header_with_*` — 标题与紧随其下的内容（text/code/list/blockquote）合并
4. `list` — 无序/有序列表块（含所有续行和子列表）
5. `blockquote` — `>` 开头的引用块（含所有续行）
6. `divider` — `---` / `***` / `___` 分隔线（**不输出**）
7. `text` — 其他普通段落

### Step 2：Section 合并为 Chunk

```text
current_chunk = ""
current_tokens = 0

for each section:
    sec_tokens = estimate_tokens(section.content)

    if current_tokens + sec_tokens > chunk_token_limit:
        output current_chunk
        if overlap_percent > 0:
            current_chunk = tail(current_chunk, overlap_percent) + section.content
        else:
            current_chunk = section.content
        current_tokens = estimate_tokens(current_chunk)
    else:
        current_chunk += "\n" + section.content
        current_tokens += sec_tokens

output current_chunk
```

### Step 3：过滤

- 丢弃 `est_tokens < min_chunk_tokens` 的 Chunk
- 超出 `chunk_token_limit` 的单个 Section，强制作为独立 Chunk（不拆分）
- `divider` 类型不输出

## Token 估算规则

```
中文字符 × 1.0
英文字符 ÷ 4（单词间空格 +0.3）
代码块字符 ÷ 4
标点和空白 ≈ 0
```

## 注意事项

- 输出**仅包含 JSON**，不要包含任何 Markdown 包裹或解释文字
- `content` 字段保留 Markdown 原始语法符号（`#`、` ``` `、`|` 等）
- 所有 `source_section_types` 和 `source_line_range` 必须准确
- `est_tokens` 为估算值，允许 ±10% 误差
- 如果文档总长度超过 5000 tokens，建议前端按自然章节拆分后分批调用
