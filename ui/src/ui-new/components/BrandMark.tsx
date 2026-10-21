import { cn } from "@/lib/utils";

/**
 * Placeholder logo for the new view (an arch over a stem) until the real HealingARK logo is
 * supplied. Swap the SVG here and every screen picks it up.
 */
export function BrandMark({ size = 32, className }: { size?: number; className?: string }) {
  return (
    <span
      role="img"
      aria-label="HealingARK"
      className={cn(
        "inline-grid place-items-center rounded-[10px] bg-primary text-white shadow-[0_6px_14px_rgba(108,63,197,.35)]",
        className
      )}
      style={{ width: size, height: size }}
    >
      <svg
        viewBox="0 0 24 24"
        width={Math.round(size * 0.62)}
        height={Math.round(size * 0.62)}
        fill="none"
        stroke="currentColor"
        strokeWidth={1.9}
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M4.5 18a7.5 7.5 0 0115 0" />
        <path d="M12 6.5v3.5" />
      </svg>
    </span>
  );
}

export default BrandMark;
