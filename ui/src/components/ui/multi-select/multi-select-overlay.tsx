import { ReactNode, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

import { cn } from "@/lib/utils";

interface MultiSelectOverlayProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;

  trigger: ReactNode;

  children: ReactNode;

  title?: string;

  /**
   * When false, the trigger wrapper doesn't intercept clicks to toggle
   * open state itself — for a live text-input trigger (e.g. Combobox) that
   * already opens on focus/typing, an unconditional click-toggle would
   * close the panel again on a second click inside an already-focused
   * input (e.g. repositioning the caret). Defaults to true, the original
   * click-to-toggle-button behavior this component was built for.
   */
  toggleOnTriggerClick?: boolean;
}

/**
 * Manual portal-based dropdown — deliberately avoids Radix Popover.
 * Confirmed via an automated test (src/components/ui/multi-select/multi-select.test.tsx)
 * that Radix Popover (even with `modal` + the correct anchor-width CSS var)
 * never renders its content when nested inside this app's modal Dialog.
 * This sidesteps that composition problem entirely: a real portal, position
 * computed from the trigger's own bounding rect.
 *
 * The portal target is the nearest ancestor `[data-slot="dialog-content"]`
 * when one exists, NOT document.body. Radix's modal Dialog uses
 * react-remove-scroll to lock background scrolling: it blocks wheel/touch
 * events everywhere on the page except inside the Dialog's own tracked DOM
 * subtree. A panel portaled to document.body sits outside that subtree, so
 * react-remove-scroll silently blocks scrolling inside it too -- the
 * dropdown's option list becomes visible but unscrollable. Portaling inside
 * DialogContent instead makes it a real descendant, so the lock treats it
 * like any other in-dialog scrollable region. document.body remains the
 * fallback for non-Dialog usage.
 *
 * The trigger-wrapper and the portal call are React *siblings* (not
 * parent/child) — createPortal only relocates DOM output, not React-tree
 * membership, so if the portal were a React child of the trigger's onClick
 * handler, clicks inside the portaled list would bubble into that handler
 * and immediately close the dropdown after every item click.
 */
export function MultiSelectOverlay({
  open,
  onOpenChange,
  trigger,
  children,
  toggleOnTriggerClick = true,
}: MultiSelectOverlayProps) {
  const anchorRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState<{
    top: number;
    left: number;
    width: number;
  } | null>(null);
  const [portalTarget, setPortalTarget] = useState<HTMLElement | null>(null);

  useEffect(() => {
    if (!open || !anchorRef.current) return;

    // DialogContent is itself `position: fixed` with a `transform` (for
    // centering), which makes it the containing block for `position:
    // fixed`/`absolute` descendants per the CSS spec -- so once portaled
    // inside it, position must be computed relative to ITS rect, not the
    // viewport, or the panel would be offset by the dialog's own position.
    const dialogContent = anchorRef.current.closest(
      '[data-slot="dialog-content"]'
    ) as HTMLElement | null;
    const container = dialogContent ?? document.body;
    setPortalTarget(container);

    // Same 280/380 sizing as before -- CSS decides the actual width within
    // that range based on content. `left` is clamped against the widest
    // that range allows (380) so the panel aligns under its trigger but
    // can never run past the container's right edge regardless of which
    // width it ends up rendering at.
    const EDGE_MARGIN = 8;
    const MAX_WIDTH = 380;

    const updatePosition = () => {
      const rect = anchorRef.current!.getBoundingClientRect();
      const isBody = container === document.body;
      const containerRect = isBody ? null : container.getBoundingClientRect();
      const containerWidth = isBody ? window.innerWidth : containerRect!.width;
      const containerLeft = isBody ? 0 : containerRect!.left;
      const containerTop = isBody ? 0 : containerRect!.top;

      const maxLeft = containerWidth - Math.min(MAX_WIDTH, containerWidth - EDGE_MARGIN * 2) - EDGE_MARGIN;
      const left = Math.min(
        Math.max(rect.left - containerLeft, EDGE_MARGIN),
        Math.max(EDGE_MARGIN, maxLeft)
      );
      const top = rect.bottom - containerTop + 4;

      setPosition({ top, left, width: rect.width });
    };

    updatePosition();
    window.addEventListener("scroll", updatePosition, true);
    window.addEventListener("resize", updatePosition);
    return () => {
      window.removeEventListener("scroll", updatePosition, true);
      window.removeEventListener("resize", updatePosition);
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node;
      if (
        anchorRef.current?.contains(target) ||
        panelRef.current?.contains(target)
      ) {
        return;
      }
      onOpenChange(false);
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onOpenChange(false);
    };

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, onOpenChange]);

  return (
    <>
      <div
        ref={anchorRef}
        onClick={toggleOnTriggerClick ? () => onOpenChange(!open) : undefined}
      >
        {trigger}
      </div>

      {open &&
        position &&
        portalTarget &&
        createPortal(
          <div
            ref={panelRef}
            style={{
              // Viewport-relative when falling back to document.body;
              // container-relative (see the position-calc effect above)
              // when portaled inside DialogContent, since that ancestor's
              // own `transform` makes it the containing block instead.
              position: portalTarget === document.body ? "fixed" : "absolute",
              top: position.top,
              left: position.left,
              minWidth: Math.max(position.width, 280),
              maxWidth: 380,
              // A modal Dialog sets `pointer-events: none` on <body> (via
              // react-remove-scroll's scroll lock) to keep interaction
              // confined to the dialog. When falling back to document.body
              // this panel is outside the dialog's own DOM subtree, so it
              // inherits that `none` unless explicitly overridden here —
              // Radix's own DialogContent does the same override on itself.
              pointerEvents: "auto",
            }}
            className={cn(
              "z-50 flex flex-col rounded-md border bg-popover text-popover-foreground shadow-md"
            )}
          >
            {children}
          </div>,
          portalTarget
        )}
    </>
  );
}
