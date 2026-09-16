"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import CommercialAreaSummary from "@/components/report/CommercialAreaSummary";
import StateMessage from "@/components/common/StateMessage";
import { getReport } from "@/lib/api";
import type { AnalysisReport, ViewState } from "@/types";

export default function AreaPage() {
  const params = useParams<{ reportId: string }>();
  const reportId = Number(params.reportId);
  const [state, setState] = useState<ViewState>("loading");
  const [report, setReport] = useState<AnalysisReport | null>(null);

  useEffect(() => {
    let alive = true;
    getReport(reportId)
      .then((r) => { if (alive) { setReport(r); setState("success"); } })
      .catch(() => { if (alive) setState("error"); });
    return () => { alive = false; };
  }, [reportId]);

  return (
    <main>
      <StateMessage state={state} message={state === "error" ? "생활권 정보를 불러오지 못했습니다." : undefined} />

      {report && (
        <>
          <section aria-label="분석 대상">
            <h1>{report.store.name} 주변 생활권</h1>
            <p className="muted">{report.store.address ?? ""}</p>
          </section>

          <CommercialAreaSummary area={report.commercialArea} />

          <section aria-label="다음 단계" className="row">
            <Link href={`/report/${reportId}`}>
              <button type="button">이번 주 운영 컨설팅 보기</button>
            </Link>
            <Link href="/search"><button type="button">다른 가게 분석</button></Link>
          </section>
        </>
      )}
    </main>
  );
}
