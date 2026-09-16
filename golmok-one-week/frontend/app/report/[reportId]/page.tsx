"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import ReportSummary from "@/components/report/ReportSummary";
import WeatherList from "@/components/report/WeatherList";
import CommercialAreaSummary from "@/components/report/CommercialAreaSummary";
import FestivalList from "@/components/report/FestivalList";
import IngredientPriceList from "@/components/report/IngredientPriceList";
import DailyGuideList from "@/components/report/DailyGuideList";
import SourceList from "@/components/report/SourceList";
import Disclaimer from "@/components/common/Disclaimer";
import StateMessage from "@/components/common/StateMessage";
import { getReport } from "@/lib/api";
import type { AnalysisReport, ViewState } from "@/types";

export default function ReportPage() {
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
      <StateMessage state={state} message={state === "error" ? "리포트를 불러오지 못했습니다." : undefined} />

      {report && (
        <>
          <ReportSummary report={report} />
          <WeatherList days={report.weather} />
          <CommercialAreaSummary area={report.commercialArea} />
          <FestivalList festivals={report.festivals} />
          <IngredientPriceList prices={report.ingredientPrices} />
          <DailyGuideList guides={report.dailyGuides} />
          <SourceList sources={report.sources} />
          <Disclaimer text={report.disclaimer} />

          <section aria-label="추가 동작" className="row">
            <Link href="/search"><button type="button">다른 가게 분석</button></Link>
            {/* 공유 기능은 UI만 제공 (MVP 범위에서 제외) */}
            <button type="button" disabled title="MVP에서는 UI만 제공합니다.">분석 결과 공유</button>
          </section>
        </>
      )}
    </main>
  );
}
