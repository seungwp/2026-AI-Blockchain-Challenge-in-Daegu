import TabBar from "@/components/layout/TabBar";

export default function ReportTabsLayout({ children }: { children: React.ReactNode }) {
  return <>{children}<TabBar /></>;
}
