import { useCallback, useEffect, useRef, useState } from "react";

/**
 * READ-03: a one-way "has the reader scrolled to the end of this document"
 * gate, backing the scroll-gated "I Agree" button in the mandatory reading
 * flow.
 *
 * Mirrors the callback-ref `IntersectionObserver` pattern already shipped in
 * `cph/ui/src/pages/PdfList.tsx` (`sentinelRef`), but adapted to be one-shot
 * instead of repeating: once the sentinel intersects, the observer
 * disconnects and `hasReachedEnd` never goes back to `false`. A window
 * resize, re-render, or any other later event must never re-lock "I Agree" —
 * this is a one-way ratchet, not a live readout of scroll position.
 *
 * Short-document case: if the rendered content is short enough that the
 * sentinel is already inside the viewport at `observe()` time, the browser
 * fires the intersection callback immediately, so the gate opens with no
 * special-casing required.
 */
export interface ScrollGateResult {
  hasReachedEnd: boolean;
  sentinelRef: (node: HTMLDivElement | null) => void;
}

export function useScrollGate(): ScrollGateResult {
  const [hasReachedEnd, setHasReachedEnd] = useState(false);
  const reachedRef = useRef(false);
  const observerRef = useRef<IntersectionObserver | null>(null);

  // NOTE: this hook must never flip the "reached end" state back to its
  // initial (not-yet-reached) value after it first becomes true, and must
  // never reset reachedRef.current after initialisation — the gate is a
  // one-way ratchet (33-RESEARCH.md's explicit anti-pattern warning).
  const sentinelRef = useCallback((node: HTMLDivElement | null) => {
    if (reachedRef.current) return;

    if (observerRef.current) observerRef.current.disconnect();

    observerRef.current = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) {
        reachedRef.current = true;
        setHasReachedEnd(true);
        observerRef.current?.disconnect();
      }
    });

    if (node) observerRef.current.observe(node);
  }, []);

  useEffect(() => {
    return () => {
      observerRef.current?.disconnect();
    };
  }, []);

  return { hasReachedEnd, sentinelRef };
}
