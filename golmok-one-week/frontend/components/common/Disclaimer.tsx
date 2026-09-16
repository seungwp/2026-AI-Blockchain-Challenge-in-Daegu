/** 면책 문구. 제거하지 않는다. */
export default function Disclaimer({ text }: { text: string }) {
  return (
    <section aria-label="면책 문구">
      <p className="notice">{text}</p>
    </section>
  );
}
