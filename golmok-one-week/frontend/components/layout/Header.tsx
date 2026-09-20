import Image from "next/image";
import Link from "next/link";

/** 공통 헤더. 디자인 없이 로고와 텍스트 링크만 둔다. */
export default function Header() {
  return (
    <header>
      <nav className="row">
        <Link href="/" className="row">
          <Image src="/brand/app-icon.svg" width={24} height={24} alt="" style={{ borderRadius: 6 }} />
          <strong>장사메이트</strong>
        </Link>
        <Link href="/search">가게 검색</Link>
      </nav>
    </header>
  );
}
