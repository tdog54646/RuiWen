import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import Script from "next/script";
import { Toaster } from "@/components/ui/sonner";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Line",
  description: "Line — 知识分享平台",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        {children}
        <Toaster position="top-center" richColors />
        <Script
          id="monaco-env"
          strategy="beforeInteractive"
          dangerouslySetInnerHTML={{
            __html: `
(function() {
  var vsBase = "/monaco-editor/vs";
  window.MonacoEnvironment = {
    getWorkerUrl: function(moduleId, label) {
      if (label === "json") return vsBase + "/assets/json.worker-DKiEKt88.js";
      if (label === "css" || label === "scss" || label === "less") return vsBase + "/assets/css.worker-HnVq6Ewq.js";
      if (label === "html" || label === "handlebars" || label === "razor") return vsBase + "/assets/html.worker-B51mlPHg.js";
      if (label === "typescript" || label === "javascript") return vsBase + "/assets/ts.worker-CMbG-7ft.js";
      return vsBase + "/assets/editor.worker-Be8ye1pW.js";
    }
  };
})();
            `,
          }}
        />
      </body>
    </html>
  );
}
