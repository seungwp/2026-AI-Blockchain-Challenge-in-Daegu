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
        <AppChrome>{children}</AppChrome>
      </body>
    </html>
  );
}
