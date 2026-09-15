import { useState, type ReactNode } from "react";
import { Link, NavLink, Navigate, Outlet, Route, Routes, useNavigate, useParams } from "react-router-dom";
import { CircleMarker, MapContainer, TileLayer } from "react-leaflet";
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { adviceData, adviceIsVisible, appIndex, getAdvice, getSales, getStore, priceData, storeList, validationData, type Json } from "./data";

const storeKey = "dongne-doctor-store";
const dateKey = "dongne-doctor-date";
const storeId = () => localStorage.getItem(storeKey) || "";
const activeDate = () => localStorage.getItem(dateKey) || appIndex.live_date;
const won = (v: unknown) => typeof v === "number" ? `${Math.round(v).toLocaleString("ko-KR")}원` : "-";
const pct = (v: unknown) => typeof v === "number" ? `${(v * 100).toFixed(1)}%` : "-";

function Shell() {
  const store = getStore(storeId());
  const [date, setDate] = useState(activeDate());
  if (!store) return <Navigate to="/login" replace />;
  const tabs = [["/home", "홈"], ["/sales", "매출"], ["/prices", "식자재"], ["/dong", "동네"]];
  return <div className="app">
    <header><div className="wrap header-row"><Link to="/home"><strong>동네 주치의</strong></Link><div className="header-tools"><span>{store.id} · {store.category}</span><select value={date} onChange={e => { setDate(e.target.value); localStorage.setItem(dateKey, e.target.value); }} aria-label="조언 날짜">{appIndex.advice_dates.map((d: string) => <option key={d}>{d}</option>)}</select><Link to="/trust">신뢰도</Link></div></div></header>
    <main className="wrap content"><Outlet /></main>
    <nav className="bottom-nav"><div className="wrap nav-grid">{tabs.map(([to, label]) => <NavLink key={to} to={to} className={({ isActive }) => isActive ? "active" : ""}>{label}</NavLink>)}</div></nav>
  </div>;
}

function Login() {
  const navigate = useNavigate();
  return <main className="wrap login"><h1>동네 주치의</h1><p>대구 음식점 사장님을 위한 이번 주 운영 조언</p><label>상호·주소 검색<input disabled placeholder="출시 시 제공" /></label><label>사업자등록번호<input disabled placeholder="출시 시 제공" /></label><p className="muted">데모 가게를 선택해 시작하세요.</p><section className="stack">{storeList.map((s: Json) => <button key={s.id} className="option" onClick={() => { localStorage.setItem(storeKey, s.id); localStorage.setItem(dateKey, appIndex.live_date); navigate("/home"); }}><strong>{s.id} {s.category}</strong><span>{s.dong} · {s.role}</span></button>)}</section></main>;
}

function AdviceCard({ item, index }: { item: Json; index: number }) { return <Link className="item" to={`/advice/${index}`}><small>{item.urgency}</small><strong>{item.title}</strong><span>{item.action}</span></Link>; }

function Home() {
  const advice = getAdvice(storeId(), activeDate());
  const store = getStore(storeId());
  const days = advice?.signals?.weather_next7?.days || [];
  return <div className="stack"><section><h1>이번 주 조언</h1><p>{store?.id} · {store?.category} · {store?.dong}</p><small>{activeDate()} · {advice?.mode === "live" ? "실시간" : "과거 샘플"}</small></section>{advice && adviceIsVisible(advice) ? <><section><h2>요약</h2><p>{advice.advice.summary}</p></section><section className="stack"><h2>조언</h2>{advice.advice.advices.map((item: Json, i: number) => <AdviceCard key={i} item={item} index={i} />)}</section></> : <p className="notice">검증된 조언을 준비 중입니다.</p>}<section><h2>다음 7일 날씨</h2>{days.map((d: Json) => <div className="row" key={d.date}><span>{d.date}</span><span>{d["최고기온"]}° / {d["최저기온"]}°</span><span>강수 {d["강수량_mm"]}mm</span></div>)}</section></div>;
}

function AdviceDetail() {
  const advice = getAdvice(storeId(), activeDate());
  const item = advice?.advice?.advices?.[Number(useParams().index)];
  if (!advice || !adviceIsVisible(advice) || !item) return <Navigate to="/home" replace />;
  return <div className="stack"><Link to="/home">← 조언 목록</Link><section><small>{item.urgency}</small><h1>{item.title}</h1><p>{item.action}</p></section><section><h2>근거 데이터</h2><p>{item.reason}</p><ul>{(item.evidence || []).map((e: string) => <li key={e}><code>{e}</code></li>)}</ul></section></div>;
}

function Sales() {
  const days = getSales(storeId())?.days || [];
  const chart = days.map((d: Json) => ({ date: String(d.date).slice(5), 홀: d.hall, 배달: d.delivery }));
  return <div className="stack"><h1>매출</h1><p className="notice">가상 매출 데모</p><div className="chart"><ResponsiveContainer><LineChart data={chart}><XAxis dataKey="date" /><YAxis tickFormatter={v => `${Math.round(v / 10000)}만`} /><Tooltip formatter={(v: number) => won(v)} /><Line dataKey="홀" dot={false} /><Line dataKey="배달" dot={false} /></LineChart></ResponsiveContainer></div><p>최근 28일 샘플 매출을 홀·배달로 나눠 표시합니다.</p></div>;
}

function Prices() {
  const items = getAdvice(storeId(), activeDate())?.signals?.ingredients || [];
  const all = priceData.items || [];
  const [selected, setSelected] = useState(items[0]?.품목 || all[0]?.item);
  const series = all.find((x: Json) => x.item === selected)?.series || [];
  return <div className="stack"><h1>식자재</h1><p className="muted">{priceData.source} · 기준일 {priceData.as_of}</p><div className="table-wrap"><table><thead><tr><th>품목</th><th>현재가</th><th>급등확률</th><th>평년대비</th></tr></thead><tbody>{items.map((x: Json) => <tr key={x.id} onClick={() => setSelected(x.품목)}><td>{x.품목}{x.급등경고 ? " · 경고" : ""}</td><td>{won(x.현재가_원)}</td><td>{x.다음주_급등확률_퍼센트}%</td><td>{x.평년대비_퍼센트}%</td></tr>)}</tbody></table></div>{!items.length && <p>이번 주 급등 경고 없음</p>}<h2>{selected} 가격 추이</h2><div className="chart"><ResponsiveContainer><LineChart data={series}><XAxis dataKey="date" /><YAxis /><Tooltip formatter={(v: number) => won(v)} /><Line dataKey="price" dot={false} /><Line dataKey="normal_price" dot={false} /></LineChart></ResponsiveContainer></div></div>;
}

function Dong() {
  const store = getStore(storeId());
  const advice = getAdvice(storeId(), activeDate());
  const center: [number, number] = [store?.dong_center?.lat || 35.87, store?.dong_center?.lon || 128.60];
  return <div className="stack"><h1>동네</h1><MapContainer center={center} zoom={13} scrollWheelZoom={false}><TileLayer attribution="© OpenStreetMap" url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" /><CircleMarker center={center} radius={8} /></MapContainer><small>정확한 가게 위치 대신 동네 중심점만 표시합니다.</small><List title="축제" rows={advice?.signals?.festivals_next7} render={(x) => `${x.이름} · ${x.기간} · ${x.거리_m}m`} /><List title="경쟁점" rows={advice?.signals?.competitors_90d} render={(x) => `${x.개업일} · ${x.업종} · ${x.거리_m}m`} /></div>;
}
function List({ title, rows, render }: { title: string; rows?: Json[]; render: (x: Json) => string }) { return <section><h2>{title}</h2>{rows?.length ? <ul className="stack">{rows.map((x, i) => <li className="item" key={i}>{render(x)}</li>)}</ul> : <p>표시할 항목이 없습니다.</p>}</section>; }

function Trust() {
  return <div className="stack"><h1>신뢰도</h1><section><h2>검증 결과</h2>{Object.values(adviceData).flatMap(x => Object.values(x)).filter((x: Json) => x.actual_after).map((x: Json) => <div className="row" key={`${x.as_of}-${x.signals.store.id}`}><span>{x.as_of} · {x.signals.store.id}</span><span>{pct(x.actual_after["다음7일_평균매출_변화율_퍼센트"])}</span></div>)}</section><Metric title="가격 급등 모델" rows={validationData.price_spike_model?.overall} /><Metric title="LLM 조언 검증" rows={validationData.advice_llm} /></div>;
}
function Metric({ title, rows }: { title: string; rows?: Json[] }) { return <section><h2>{title}</h2><div className="table-wrap"><table><tbody>{(rows || []).map((r, i) => <tr key={i}><td>{Object.entries(r).map(([k, v]) => `${k}: ${typeof v === "number" && v < 1 ? pct(v) : String(v)}`).join(" · ")}</td></tr>)}</tbody></table></div></section>; }

function Protected() { return storeId() ? <Outlet /> : <Navigate to="/login" replace />; }
export default function App() { return <Routes><Route path="/login" element={<Login />} /><Route element={<Protected />}><Route element={<Shell />}><Route path="/home" element={<Home />} /><Route path="/advice/:index" element={<AdviceDetail />} /><Route path="/sales" element={<Sales />} /><Route path="/prices" element={<Prices />} /><Route path="/dong" element={<Dong />} /><Route path="/trust" element={<Trust />} /></Route></Route><Route path="*" element={<Navigate to={storeId() ? "/home" : "/login"} replace />} /></Routes>; }
