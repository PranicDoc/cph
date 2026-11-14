import { createContext } from "react";
import type { ReviewStreamEvent } from "./useReviewStream";

/** What the layout-level stream shares with the pages and the nav. */
export interface ReviewStreamValue {
  unreadCount: number;
  resetUnread: () => void;
  /** Registers a listener for every event; returns the function that removes it. */
  subscribe: (listener: (e: ReviewStreamEvent) => void) => () => void;
}

/** Null outside a provider: a page then opens its own stream (as in unit tests). */
export const ReviewStreamContext = createContext<ReviewStreamValue | null>(null);
