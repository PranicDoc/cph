import { cn } from "@/lib/utils";

/**
 * The HealingARK logo: the white ark on a purple tile, the same mark the landing page shows
 * beside the name. Kept in step with public/favicon.svg and public/email/healingark-mark.png.
 */
export function BrandMark({ size = 32, className }: { size?: number; className?: string }) {
  return (
    <span
      role="img"
      aria-label="HealingARK"
      className={cn(
        "inline-block shrink-0 overflow-hidden rounded-[25%] shadow-[0_6px_14px_rgba(108,63,197,.35)]",
        className
      )}
      style={{ width: size, height: size }}
    >
      <svg viewBox="0 0 64 64" width={size} height={size} aria-hidden="true" className="block">
        <rect width="64" height="64" fill="#6c3fc5" />
        <path
          d="M12 42c6-16 14-24 20-24s14 8 20 24c-6-6-12-9-20-9s-14 3-20 9z"
          fill="#fff"
          fillOpacity=".95"
        />
      </svg>
    </span>
  );
}

export default BrandMark;
