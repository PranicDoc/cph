import { ReactNode, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

import { cn } from "@/lib/utils";

interface MultiSelectOverlayProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;

  trigger: ReactNode;

  children: ReactNode;

  title?: string;
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

    const updatePosition = () => {
      const rect = anchorRef.current!.getBoundingClientRect();
      if (container === document.body) {
        setPosition({
          top: rect.bottom + 4,
          left: rect.left,
          width: rect.width,
        });
      } else {
        const containerRect = container.getBoundingClientRect();
        setPosition({
          top: rect.bottom - containerRect.top + 4,
          left: rect.left - containerRect.left,
          width: rect.width,
        });
      }
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
      <div ref={anchorRef} onClick={() => onOpenChange(!open)}>
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
