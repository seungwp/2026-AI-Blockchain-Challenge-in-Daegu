import type { ViewState } from "@/types";

/** 화면 상태(initial/loading/empty/error) 공통 표시. success 는 호출부에서 내용 렌더링. */
export default function StateMessage({ state, message }: { state: ViewState; message?: string }) {
  if (state === "success") return null;
  if (state === "loading") return <p role="status">불러오는 중입니다...</p>;
  if (state === "empty") return <p role="status">{message ?? "결과가 없습니다."}</p>;
  if (state === "error") return <p role="alert" className="error">{message ?? "오류가 발생했습니다."}</p>;
  return message ? <p className="muted">{message}</p> : null;
}
