"use client";

import { useState } from "react";
import { askReportQuestion } from "@/lib/api";
import type { ChatMessage } from "@/types";

/**
 * 우측 하단 플로팅 챗봇. 지금 보고 있는 리포트 데이터만 근거로 답한다(일반 지식 챗봇 아님).
 * 위치(fixed, 우측 하단)만 고정하고 그 외 디자인은 넣지 않았다 — 디자인팀이 자유롭게 스타일링.
 */
export default function ChatWidget({ reportId }: { reportId: number }) {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);

  async function send() {
    const question = input.trim();
    if (!question || loading) return;
    setInput("");
    const nextHistory = [...messages, { role: "user", content: question } as ChatMessage];
    setMessages(nextHistory);
    setLoading(true);
    try {
      const answer = await askReportQuestion(reportId, question, messages);
      setMessages([...nextHistory, { role: "assistant", content: answer }]);
    } catch {
      setMessages([...nextHistory, { role: "assistant", content: "답변을 가져오지 못했습니다. 잠시 후 다시 시도해주세요." }]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ position: "fixed", right: "16px", bottom: "16px", zIndex: 50 }}>
      {open && (
        <section
          aria-label="리포트 챗봇"
          style={{ width: "min(92vw, 320px)", maxHeight: "60vh", display: "flex", flexDirection: "column", marginBottom: "8px" }}
          className="card"
        >
          <div className="row">
            <strong>이번 주 리포트에 대해 물어보세요</strong>
            <button type="button" onClick={() => setOpen(false)} aria-label="챗봇 닫기">×</button>
          </div>
          <p className="muted">답변은 이 리포트에 있는 사실만 근거로 하며, 매출 예측은 하지 않습니다.</p>

          <div style={{ overflowY: "auto", flex: 1 }}>
            {messages.length === 0 && (
              <p className="muted">예: &quot;이번 주 비 오는 날 언제예요?&quot;, &quot;식자재 가격 급등 확인 필요한 게 있나요?&quot;</p>
            )}
            <ul style={{ listStyle: "none", paddingLeft: 0 }}>
              {messages.map((m, i) => (
                <li key={i} className={m.role === "user" ? "chat-user" : "chat-assistant"}>
                  <p>{m.content}</p>
                </li>
              ))}
              {loading && <li aria-live="polite">답변을 준비하고 있습니다...</li>}
            </ul>
          </div>

          <form
            onSubmit={(e) => { e.preventDefault(); send(); }}
            className="row"
          >
            <label htmlFor="chatInput" style={{ position: "absolute", left: "-9999px" }}>질문 입력</label>
            <input
              id="chatInput"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="질문을 입력해주세요"
              disabled={loading}
            />
            <button type="submit" disabled={loading || !input.trim()}>전송</button>
          </form>
        </section>
      )}

      <button type="button" onClick={() => setOpen((v) => !v)} aria-label="리포트 챗봇 열기">
        {open ? "닫기" : "챗봇"}
      </button>
    </div>
  );
}
