"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useRef } from "react";
import type { ReactNode } from "react";
import styles from "./onboarding.module.css";

export function Icon({ name }: { name: "back" | "search" | "location" | "enter" | "chip" }) {
  return <Image src={`/onboarding/${name}.svg`} width={18} height={18} alt="" aria-hidden="true" />;
}

export default function OnboardingShell({ step, back, eyebrow, title, description, children, action }: {
  step: number; back?: string; eyebrow?: string; title?: ReactNode; description?: string;
  children: ReactNode; action?: ReactNode;
}) {
  const shell = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const viewport = window.visualViewport;
    const element = shell.current;
    if (!element) return;
    let frame = 0;
    const update = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        if (viewport && viewport.scale !== 1) return;
        const height = viewport?.height ?? window.innerHeight;
        element.style.setProperty("--visible-height", `${height}px`);
        element.style.setProperty("--visible-top", `${viewport?.offsetTop ?? 0}px`);
        element.dataset.compact = String(height < 650);
        const focused = document.activeElement;
        if (focused instanceof HTMLElement && element.contains(focused) && focused.matches("input, select, textarea")) {
          focused.scrollIntoView({ block: "nearest", inline: "nearest" });
        }
      });
    };
    update();
    viewport?.addEventListener("resize", update);
    viewport?.addEventListener("scroll", update);
    window.addEventListener("resize", update);
    element.addEventListener("focusin", update);
    return () => {
      cancelAnimationFrame(frame);
      viewport?.removeEventListener("resize", update);
      viewport?.removeEventListener("scroll", update);
      window.removeEventListener("resize", update);
      element.removeEventListener("focusin", update);
    };
  }, []);

  return <div className={`${styles.shell} ${step === 1 ? styles.welcomeShell : ""}`} ref={shell}>
    {back && <div className={styles.navigation}>
      <Link href={back} className={styles.back} aria-label="이전 단계로"><Icon name="back" /></Link>
      <div className={styles.progress} role="progressbar" aria-label="온보딩 진행" aria-valuemin={2} aria-valuemax={4} aria-valuenow={step}>
        <span style={{ width: `${((step - 1) / 3) * 100}%` }} />
      </div>
    </div>}
    <main className={`${styles.content} ${step === 1 ? styles.welcome : ""}`}>
      {title && <div className={styles.heading}>{eyebrow && <p className={styles.eyebrow}>{eyebrow}</p>}<h1>{title}</h1>{description && <p>{description}</p>}</div>}
      {children}
    </main>
    {action && <div className={styles.actions}>{action}</div>}
  </div>;
}
