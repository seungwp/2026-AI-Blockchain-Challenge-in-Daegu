"use client";

import { useParams } from "next/navigation";
import MainDashboard from "@/components/report/MainDashboard";
import StateMessage from "@/components/common/StateMessage";
import { useReport } from "@/lib/history";
import styles from "@/components/report/main-dashboard.module.css";

export default function ReportPage() {
  const reportId = Number(useParams<{ reportId: string }>().reportId);
  const { state, report } = useReport(reportId);
  if (!report) {
    return <main className={styles.main}><StateMessage state={state} message={state === "error" ? "리포트를 불러오지 못했습니다." : undefined} /></main>;
  }
  return <MainDashboard report={report} reportId={reportId} />;
}
