"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import MenuCategorySelector from "@/components/store/MenuCategorySelector";
import StateMessage from "@/components/common/StateMessage";
import DemoBadge from "@/components/common/DemoBadge";
import { classifyMenu, createReport, getStore } from "@/lib/api";
import { MENU_CATEGORIES } from "@/types";
import type { MenuCategory, MenuClassification, Store, ViewState } from "@/types";

const schema = z.object({
  mainMenu: z.string().trim().min(1, "대표 메뉴를 입력해주세요."),
  menuCategory: z.enum(MENU_CATEGORIES),
});

type ConfirmValues = z.infer<typeof schema>;

export default function StoreConfirmPage() {
  const router = useRouter();
  const params = useParams<{ storeId: string }>();
  const storeId = Number(params.storeId);

  const [state, setState] = useState<ViewState>("loading");
  const [store, setStore] = useState<Store | null>(null);
  const [classification, setClassification] = useState<MenuClassification | null>(null);
  const [error, setError] = useState<string>();

  const { register, handleSubmit, watch, setValue, formState } = useForm<ConfirmValues>({
    resolver: zodResolver(schema),
    defaultValues: { mainMenu: "", menuCategory: "기타" },
  });
  const mainMenu = watch("mainMenu");
  const menuCategory = watch("menuCategory");

  useEffect(() => {
    let alive = true;
    getStore(storeId)
      .then((s) => { if (alive) { setStore(s); setState("success"); } })
      .catch(() => { if (alive) { setError("가게 정보를 불러오지 못했습니다."); setState("error"); } });
    return () => { alive = false; };
  }, [storeId]);

  /** 메뉴 입력이 멈추면 자동 분류 API 호출 */
  useEffect(() => {
    const menu = mainMenu.trim();
    if (!menu) { setClassification(null); return; }
    const timer = setTimeout(async () => {
      const result = await classifyMenu(menu, store?.category ?? undefined);
      setClassification(result);
      setValue("menuCategory", result.menuCategory);
    }, 400);
    return () => clearTimeout(timer);
  }, [mainMenu, store?.category, setValue]);

  async function onSubmit(values: ConfirmValues) {
    setError(undefined);
    try {
      const report = await createReport(storeId, values.mainMenu.trim(), values.menuCategory);
      router.push(`/report/${report.reportId}/area`);
    } catch {
      setError("분석 요청 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }
  }

  return (
    <main>
      <h1>대표 메뉴·영업 형태 확인</h1>
      <StateMessage state={state === "success" ? "success" : state} message={error} />

      {store && (
        <>
          <section aria-label="선택한 가게">
            <div className="row">
              <h2>{store.name}</h2>
              <DemoBadge show={store.isDemoData} />
            </div>
            <p className="muted">영업 형태: {store.category ?? "정보 없음"}</p>
            <p>{store.address}</p>
          </section>

          <form onSubmit={handleSubmit(onSubmit)}>
            <section aria-label="대표 메뉴 입력">
              <label htmlFor="mainMenu">대표 메뉴</label>
              <input id="mainMenu" placeholder="예: 닭똥집" {...register("mainMenu")} />
              {formState.errors.mainMenu && (
                <p role="alert" className="error">{formState.errors.mainMenu.message}</p>
              )}

              {classification && (
                <p className="muted">
                  자동 분류 결과: <strong>{classification.menuCategory}</strong> (신뢰도 {classification.confidence}
                  {classification.matchedKeywords.length ? `, 키워드: ${classification.matchedKeywords.join(", ")}` : ""})
                  {classification.confidence === "LOW" && " · 아래에서 직접 선택해주세요."}
                </p>
              )}

              <MenuCategorySelector
                value={menuCategory as MenuCategory}
                onChange={(v) => setValue("menuCategory", v)}
              />

              <p>
                <button type="submit" disabled={!mainMenu.trim() || formState.isSubmitting}>
                  {formState.isSubmitting ? "분석 중..." : "이번 주 분석하기"}
                </button>
              </p>
            </section>
          </form>
        </>
      )}
    </main>
  );
}
