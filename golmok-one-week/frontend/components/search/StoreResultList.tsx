import Link from "next/link";
import type { Store } from "@/types";
import DemoBadge from "@/components/common/DemoBadge";

export default function StoreResultList({ stores }: { stores: Store[] }) {
  return (
    <ul style={{ listStyle: "none", paddingLeft: 0 }}>
      {stores.map((store) => (
        <li key={store.id} className="card">
          <div className="row">
            <strong>{store.name}</strong>
            <DemoBadge show={store.isDemoData} />
          </div>
          <p className="muted">{store.category}</p>
          <p>{store.address}</p>
          <Link href={`/store/${store.id}/confirm`}>
            <button type="button">이 가게 선택</button>
          </Link>
        </li>
      ))}
    </ul>
  );
}
