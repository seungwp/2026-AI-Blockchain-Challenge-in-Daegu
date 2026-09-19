"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import MainDashboard from "@/components/report/MainDashboard";
import StateMessage from "@/components/common/StateMessage";
import { getReport } from "@/lib/api";
import type { AnalysisReport, ViewState } from "@/types";

export default function ReportPage() {
  const { reportId: rawReportId } = useParams<{ reportId: string }>();
  const reportId = Number(rawReportId);
  const [state, setState] = useState<ViewState>("loading");
  const [report, setReport] = useState<AnalysisReport | null>(null);

  useEffect(() => {
    let alive = true;
    getReport(reportId)
      .then((value) => { if (alive) { setReport(value); setState("success"); } })
      .catch(() => { if (alive) setState("error"); });
    return () => { alive = false; };
  }, [reportId]);

  if (state !== "success" || !report) {
    return <main><StateMessage state={state} message={state === "error" ? "리포트를 불러오지 못했습니다." : undefined} /></main>;
  }
  return <MainDashboard report={report} reportId={reportId} />;
}
