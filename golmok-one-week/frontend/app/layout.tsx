import type { Metadata, Viewport } from "next";
import "./globals.css";
import AppChrome from "@/components/layout/AppChrome";

export const metadata: Metadata = {
  title: "장사메이트",
  description: "대구 골목상권 음식점 사장님을 위한 이번 주 운영 가이드 (운영 참고용)",
  appleWebApp: { title: "장사메이트", capable: true, statusBarStyle: "default" },
};

/** 홈 화면 추가·주소창 색: 브랜드 초록 (app/icon.svg·apple-icon.png·manifest.webmanifest와 한 세트) */
export const viewport: Viewport = { themeColor: "#2AAB59" };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        {/* 토스식 타이포그래피에 맞춘 본문 글꼴(SIL OFL). 불러오지 못하면 기존 Noto·시스템 글꼴로 대체 */}
        <link rel="stylesheet" precedence="default"
          href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css" />
        <AppChrome>{children}</AppChrome>
      </body>
    </html>
  );
}
