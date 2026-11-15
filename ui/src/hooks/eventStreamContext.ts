import { createContext } from "react";
import type { PdfReadyStreamEvent, ReviewStreamEvent } from "./useEventStream";

/** What the layout-level stream shares with the pages and the nav. */
export interface EventStreamValue {
  unreadCount: number;
  resetUnread: () => void;
  /** Registers a listener for every event; returns the function that removes it. */
  subscribe: (listener: (e: ReviewStreamEvent) => void) => () => void;
  /** Registers a listener for every `pdf-ready` event; returns the function that removes it. */
  subscribePdfReady: (listener: (e: PdfReadyStreamEvent) => void) => () => void;
}

/** Null outside a provider: a page then opens its own stream (as in unit tests). */
export const EventStreamContext = createContext<EventStreamValue | null>(null);
