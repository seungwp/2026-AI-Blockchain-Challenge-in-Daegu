"use client";

import { usePathname } from "next/navigation";
import Header from "./Header";
import Footer from "./Footer";

export default function AppChrome({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  const onboarding = path === "/" || path === "/search" || /^\/store\/[^/]+\/(confirm|connect)$/.test(path);
  const mainDashboard = /^\/report\/[^/]+$/.test(path);
  return <>{!onboarding && !mainDashboard && <Header />}{children}{!onboarding && !mainDashboard && <Footer />}</>;
}
