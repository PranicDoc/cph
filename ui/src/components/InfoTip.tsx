import { useRef, useState } from "react";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";

/**
 * The small "i" beside a label. Its help text opens on hover with a mouse and on tap with a finger, and
 * closes on an outside tap or Escape: helper text lives here instead of under the field.
 */
export function InfoTip({ text, label = "More information" }: { text: string; label?: string }) {
  const [open, setOpen] = useState(false);
  // A touch fires a mouseenter before its click, so what opened the tip decides what the click does.
  const pointer = useRef<string>("mouse");

  return (
    <Popover open={open} onOpenChange={(next) => { if (!next) setOpen(false); }}>
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-label={label}
          aria-expanded={open}
          onPointerDown={(e) => { pointer.current = e.pointerType; }}
          onPointerEnter={(e) => { if (e.pointerType === "mouse") setOpen(true); }}
          onPointerLeave={(e) => { if (e.pointerType === "mouse") setOpen(false); }}
          onClick={(e) => {
            e.preventDefault();
            setOpen((wasOpen) => (pointer.current === "mouse" ? true : !wasOpen));
          }}
          className="inline-flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-full border border-current text-[11px] font-bold italic leading-none text-muted-foreground hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          i
        </button>
      </PopoverTrigger>
      <PopoverContent
        side="top"
        align="start"
        onOpenAutoFocus={(e) => e.preventDefault()}
        className="w-52 gap-0 p-2 text-xs leading-snug"
      >
        {text}
      </PopoverContent>
    </Popover>
  );
}
