import Link from "next/link";

/** 공통 헤더. 디자인 없이 텍스트 링크만 둔다. */
export default function Header() {
  return (
    <header>
      <nav className="row">
        <Link href="/"><strong>장사메이트</strong></Link>
        <Link href="/search">가게 검색</Link>
      </nav>
    </header>
  );
}
