"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import type { CSSProperties } from "react";
import styles from "@/components/report/main-dashboard.module.css";

const tabs = [
  { path: "", icon: "home", label: "홈" },
  { path: "/history", icon: "history", label: "처방 이력" },
  { path: "/chat", icon: "chat", label: "챗봇" },
  { path: "/my", icon: "my", label: "내 페이지" },
];

/** 하단 탭 4개 (Figma 1차). 지난 주 상세는 탭 없이 뒤로가기만 둔다. 생활권 분석은 내 페이지 하위. */
export default function TabBar() {
  const { reportId } = useParams<{ reportId: string }>();
  const base = "/report/" + reportId;
  const rest = usePathname().slice(base.length);
  if (/^\/history\/[^/]+/.test(rest)) return null;
  const current = rest.startsWith("/area") ? "/my" : tabs.find((t) => t.path && rest.startsWith(t.path))?.path ?? "";
  return <nav className={styles.bottomNav} aria-label="주요 메뉴">
    {tabs.map((t) => <Link key={t.icon} href={base + t.path} className={t.path === current ? styles.activeNav : undefined}
      aria-current={t.path === current ? "page" : undefined}>
      <span className={styles.navIcon} style={{ "--icon": `url(/main/${t.icon}.svg)` } as CSSProperties} aria-hidden="true" />{t.label}
    </Link>)}
  </nav>;
}
