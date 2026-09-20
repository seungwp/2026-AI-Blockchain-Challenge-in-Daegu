import Image from "next/image";
import Link from "next/link";
import OnboardingShell from "@/components/onboarding/OnboardingShell";
import styles from "@/components/onboarding/onboarding.module.css";

export default function HomePage() {
  return <OnboardingShell step={1} action={<Link className={styles.primary} href="/search">가게 등록하러 가기</Link>}>
    <p className={styles.brand}>
      <Image src="/brand/app-icon.svg" width={34} height={34} alt="" priority />장사메이트
    </p>
    <h1>이번 주 장사,<br />뭘 준비해야 할까요?</h1>
    <p className={styles.welcomeDescription}>날씨·행사·식자재 가격까지, 사장님 가게 기준으로 모아 매주 할 일로 알려드려요.</p>
    <div className={styles.portrait}>
      <Image src="/onboarding/owner.png" width={304} height={304} alt="초록 앞치마를 입고 엄지를 든 사장님" priority />
    </div>
  </OnboardingShell>;
}
