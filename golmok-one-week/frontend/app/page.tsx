import Image from "next/image";
import Link from "next/link";
import OnboardingShell from "@/components/onboarding/OnboardingShell";
import styles from "@/components/onboarding/onboarding.module.css";

export default function HomePage() {
  return <OnboardingShell step={1} action={<Link className={styles.primary} href="/search">시작하기</Link>}>
    <p className={styles.greeting}>사장님, 반가워요!</p>
    <h1>언제 어디서든,<br />이번 주 장사를 챙겨보세요</h1>
    <div className={styles.portrait}>
      <Image src="/onboarding/owner.png" width={280} height={280} alt="초록 앞치마를 입고 엄지를 든 사장님" priority />
    </div>
  </OnboardingShell>;
}
