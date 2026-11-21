import { useState } from "react";
import { createPortal } from "react-dom";
import PdfDocumentView from "./PdfDocumentView";

export interface Pin {
  id: string;
  page: number;
  /** 0-1 against the page's rendered width / height. */
  x: number;
  y: number;
  label: string;
  resolved?: boolean;
  /** A pin not yet saved (waiting for its comment text). */
  draft?: boolean;
}

interface Props {
  fileUrl: string;
  pins: Pin[];
  activePinId?: string | null;
  onSelectPin?: (id: string) => void;
  /** When set, clicking a page adds a pin there; leave unset for a read-only view. */
  onAddPin?: (page: number, x: number, y: number) => void;
}

/**
 * The PDF with pins overlaid on each page. Coordinates are normalised to 0-1 against the page's
 * rendered box, so a pin lands on the same spot at any width (phone or desktop).
 */
export default function PdfPinCanvas({ fileUrl, pins, activePinId, onSelectPin, onAddPin }: Props) {
  const [pageEls, setPageEls] = useState<HTMLElement[]>([]);

  const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!onAddPin) return;
    const target = e.target as HTMLElement;
    const pageEl = target.closest<HTMLElement>("[data-pdf-page]");
    if (!pageEl) return;
    const rect = pageEl.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;
    const clamp = (v: number) => Math.min(1, Math.max(0, v));
    onAddPin(
      Number(pageEl.dataset.pdfPage),
      clamp((e.clientX - rect.left) / rect.width),
      clamp((e.clientY - rect.top) / rect.height)
    );
  };

  return (
    <div className={onAddPin ? "cursor-crosshair" : undefined} onClick={handleClick}>
      <PdfDocumentView fileUrl={fileUrl} onPagesReady={setPageEls} />
      {pageEls.map((el, i) =>
        createPortal(
          <>
            {pins
              .filter((p) => p.page === i + 1)
              .map((p) => (
                <button
                  key={p.id}
                  type="button"
                  aria-label={p.draft ? "New pin" : `Pin ${p.label}`}
                  aria-pressed={p.id === activePinId}
                  data-pin-id={p.id}
                  onClick={(e) => {
                    e.stopPropagation();
                    if (!p.draft) onSelectPin?.(p.id);
                  }}
                  style={{ left: `${p.x * 100}%`, top: `${p.y * 100}%` }}
                  className={
                    "absolute z-10 flex h-6 w-6 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full border-2 border-white text-xs font-semibold text-white shadow " +
                    (p.draft ? "bg-blue-500 " : p.resolved ? "bg-gray-400 " : "bg-red-500 ") +
                    (p.id === activePinId ? "ring-2 ring-blue-500" : "")
                  }
                >
                  {p.label}
                </button>
              ))}
          </>,
          el,
          `pins-${i + 1}`
        )
      )}
    </div>
  );
}
