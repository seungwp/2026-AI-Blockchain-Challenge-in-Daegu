"use client";

import { useEffect, useState } from "react";
import { z } from "zod";
import { getReport } from "./api";
import type { AnalysisReport, ViewState } from "@/types";

/** "9월 3주차" — 그 달 1일의 요일을 기준으로 센다. */
export function weekLabel(isoDate: string) {
  const d = new Date(isoDate + "T00:00:00");
  const firstDay = new Date(d.getFullYear(), d.getMonth(), 1).getDay();
  return (d.getMonth() + 1) + "월 " + Math.ceil((d.getDate() + firstDay) / 7) + "주차";
}
export const shortDate = (isoDate: string) => isoDate.slice(5).replace("-", ".");

const entrySchema = z.object({
  reportId: z.number(), storeId: z.number(), storeName: z.string(),
  start: z.string(), end: z.string(), count: z.number(),
});
export type WeekEntry = z.infer<typeof entrySchema>;
const key = "golmok:history:v1";

// ponytail: 처방 이력은 이 브라우저(localStorage)에만 쌓인다. 로그인·가게 저장이 생기면 백엔드 이력 API로 교체.
export function readHistory(): WeekEntry[] {
  try {
    const parsed = z.array(entrySchema).safeParse(JSON.parse(localStorage.getItem(key) ?? "[]"));
    return parsed.success ? parsed.data : [];
  } catch { return []; }
}
function recordReport(report: AnalysisReport) {
  const entry: WeekEntry = {
    reportId: report.reportId, storeId: report.store.id, storeName: report.store.name,
    start: report.analysisStartDate, end: report.analysisEndDate, count: report.topActions.length,
  };
  // 같은 가게·같은 주는 마지막으로 받은 리포트 하나만 남긴다.
  const list = [entry, ...readHistory().filter((e) => e.reportId !== entry.reportId
    && !(e.storeId === entry.storeId && e.start === entry.start))];
  list.sort((a, b) => b.start.localeCompare(a.start));
  try { localStorage.setItem(key, JSON.stringify(list.slice(0, 30))); } catch { /* 저장 불가 환경은 이력 없이 진행 */ }
}

/** 탭 화면 공통 리포트 조회. 불러온 리포트는 처방 이력에 기록한다. */
export function useReport(reportId: number) {
  const [state, setState] = useState<ViewState>("loading");
  const [report, setReport] = useState<AnalysisReport | null>(null);
  useEffect(() => {
    let alive = true;
    getReport(reportId)
      .then((value) => { if (alive) { recordReport(value); setReport(value); setState("success"); } })
      .catch(() => { if (alive) setState("error"); });
    return () => { alive = false; };
  }, [reportId]);
  return { state, report };
}
