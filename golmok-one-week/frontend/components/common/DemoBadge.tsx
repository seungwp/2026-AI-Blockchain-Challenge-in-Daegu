/** isDemoData=true 인 데이터임을 알리는 표기. 디자인이 바뀌어도 이 표기는 유지한다. */
export default function DemoBadge({ show = true, label = "데모 데이터" }: { show?: boolean; label?: string }) {
  if (!show) return null;
  return <span className="badge">{label}</span>;
}
