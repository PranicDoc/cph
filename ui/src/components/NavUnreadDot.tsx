import { useContext } from "react";
import { EventStreamContext } from "../hooks/eventStreamContext";
import { cn } from "../lib/utils";

/**
 * A small dot on a nav item that has news (review events since the user last opened the review pages).
 * Renders nothing when there is none, or outside the live event stream.
 */
export function NavUnreadDot({ className }: { className?: string }) {
  const stream = useContext(EventStreamContext);
  if (!stream || stream.unreadCount === 0) return null;
  return (
    <span
      role="img"
      aria-label="New activity"
      className={cn("ml-auto inline-block h-2 w-2 shrink-0 rounded-full bg-red-500", className)}
    />
  );
}
