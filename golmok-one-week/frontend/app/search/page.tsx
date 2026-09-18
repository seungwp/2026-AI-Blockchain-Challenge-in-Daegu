"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import StoreSearchForm from "@/components/search/StoreSearchForm";
import StoreResultList from "@/components/search/StoreResultList";
import StateMessage from "@/components/common/StateMessage";
import { createStoreFromAddress, searchStores } from "@/lib/api";
import type { Store, ViewState } from "@/types";

export default function SearchPage() {
  const router = useRouter();
  const [state, setState] = useState<ViewState>("initial");
  const [stores, setStores] = useState<Store[]>([]);
  const [keyword, setKeyword] = useState("");
  const [manualAddress, setManualAddress] = useState("");
  const [error, setError] = useState<string>();

  async function handleSearch(value: string) {
    setKeyword(value);
    setState("loading");
    setError(undefined);
    try {
      const result = await searchStores(value);
      setStores(result);
      setState(result.length ? "success" : "empty");
    } catch {
      setError("검색 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.");
      setState("error");
    }
  }

  async function handleManual() {
    if (!manualAddress.trim()) return;
    setState("loading");
    try {
      const store = await createStoreFromAddress(manualAddress.trim());
      router.push(`/store/${store.id}/confirm`);
    } catch (error) {
      setError(error instanceof Error ? error.message : "주소 등록 중 문제가 발생했습니다.");
      setState("error");
    }
  }

  return (
    <main>
      <h1>가게 검색</h1>
      <StoreSearchForm onSearch={handleSearch} defaultKeyword={keyword} />

      <StateMessage
        state={state}
        message={
          state === "initial" ? "상호명 또는 주소를 입력해 검색해주세요."
            : state === "empty" ? "검색 결과가 없습니다. 아래에 주소를 직접 입력해주세요."
            : error
        }
      />

      {state === "success" && <StoreResultList stores={stores} />}

      {(state === "empty" || state === "error") && (
        <section aria-label="주소 직접 입력">
          <h2>주소 직접 입력</h2>
          <label htmlFor="manualAddress">가게 주소</label>
          <div className="row">
            <input
              id="manualAddress"
              value={manualAddress}
              placeholder="예: 대구광역시 중구 동성로 1"
              onChange={(e) => setManualAddress(e.target.value)}
            />
            <button type="button" onClick={handleManual} disabled={!manualAddress.trim()}>
              이 주소로 계속하기
            </button>
          </div>
        </section>
      )}
    </main>
  );
}
