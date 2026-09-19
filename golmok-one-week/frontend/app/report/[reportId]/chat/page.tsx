"use client";

import Image from "next/image";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import StateMessage from "@/components/common/StateMessage";
import { askReportQuestion } from "@/lib/api";
import { useReport } from "@/lib/history";
import type { ChatMessage } from "@/types";
import styles from "@/components/report/main-dashboard.module.css";

/** 챗봇 탭 (Figma 1차 73:445). 리포트 데이터만 근거로 답하고, 매출 예측 질문은 거절한다(백엔드 정책). */
export default function ChatPage() {
  const reportId = Number(useParams<{ reportId: string }>().reportId);
  const { state, report } = useReport(reportId);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => { end.current?.scrollIntoView({ block: "end" }); }, [messages, loading]);

  async function send(text: string) {
    const question = text.trim();
    if (!question || loading) return;
    setInput("");
    const next = [...messages, { role: "user", content: question } as ChatMessage];
    setMessages(next);
    setLoading(true);
    try {
      const answer = await askReportQuestion(reportId, question, messages);
      setMessages([...next, { role: "assistant", content: answer }]);
    } catch {
      setMessages([...next, { role: "assistant", content: "답변을 가져오지 못했어요. 잠시 후 다시 시도해주세요." }]);
    } finally {
      setLoading(false);
    }
  }

  const first = report?.topActions[0];
  const examples = [first && `왜 '${first.title.split(" · ").pop()}' 해야 해요?`, "이번 주 매출 알려줘"].filter(Boolean) as string[];

  return <main className={`${styles.main} ${styles.chatMain}`}>
    <header className={styles.chatHeader}>
      <span className={styles.aiAvatar} aria-hidden="true">AI</span>
      <div><h1>AI 챗봇</h1><p>이번 주 처방에 대해 편하게 물어보세요</p></div>
    </header>
    {!report ? <StateMessage state={state} message={state === "error" ? "리포트를 불러오지 못했습니다." : undefined} /> : <>
      <div className={styles.botRow}><p className={styles.botBubble}>안녕하세요, 사장님! 이번 주 처방 중 궁금한 게 있으면 편하게 물어보세요.</p></div>
      {!messages.length && <div className={styles.chips}>{examples.map((q) => <button key={q} type="button" onClick={() => send(q)}>{q}</button>)}</div>}
      {messages.map((m, i) => m.role === "user"
        ? <p key={i} className={styles.userBubble}>{m.content}</p>
        : <div key={i} className={styles.botRow}><p className={styles.botBubble}>{m.content}</p></div>)}
      {loading && <div className={styles.botRow} role="status"><p className={styles.botBubble}>답변을 준비하고 있어요…</p></div>}
      {!loading && messages.at(-1)?.role === "assistant" && <Link href={"/report/" + reportId} className={styles.relatedLink}>관련 처방 카드 보기 ›</Link>}
      <div ref={end} />
      <form className={styles.chatInput} onSubmit={(e) => { e.preventDefault(); send(input); }}>
        <label htmlFor="chatInput" className={styles.visuallyHidden}>질문 입력</label>
        <input id="chatInput" value={input} onChange={(e) => setInput(e.target.value)} placeholder="궁금한 점을 입력하세요" disabled={loading} />
        <button type="submit" disabled={loading || !input.trim()} aria-label="보내기"><Image src="/main/send.svg" width={24} height={24} alt="" /></button>
      </form>
    </>}
  </main>;
}
