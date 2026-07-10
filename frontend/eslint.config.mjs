import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next。
  // 已构建的第三方产物（Monaco 编辑器、编辑器 workers/语法包）体积大、含大量压缩
  // JS，不是项目源码，不应参与 lint，否则会扫出数万条无关的规则告警并导致 CI 失败。
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // 已构建的 Monaco 编辑器及关联 workers/语法 chunk（第三方产物）
    "public/monaco-editor/**",
  ]),
]);

export default eslintConfig;
