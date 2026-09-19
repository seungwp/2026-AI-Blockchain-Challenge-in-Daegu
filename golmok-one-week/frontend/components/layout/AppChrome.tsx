"use client";

import { usePathname } from "next/navigation";
import Header from "./Header";
import Footer from "./Footer";

export default function AppChrome({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  const onboarding = path === "/" || path === "/search" || /^\/store\/[^/]+\/(confirm|connect)$/.test(path);
  const tabs = path.startsWith("/report/");
  return <>{!onboarding && !tabs && <Header />}{children}{!onboarding && !tabs && <Footer />}</>;
}
