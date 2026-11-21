import type { Pin } from "./PdfPinCanvas";
import type { ReviewComment } from "./reviewApi";

export interface Thread {
  root: ReviewComment;
  replies: ReviewComment[];
  /** 1-based pin number for a pinned thread, stable across versions; null for a general thread. */
  pinNumber: number | null;
}

/** Groups comments into threads, oldest first; pinned threads are numbered in creation order. */
export function buildThreads(comments: ReviewComment[]): Thread[] {
  const sorted = [...comments].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const repliesByParent = new Map<string, ReviewComment[]>();
  for (const c of sorted) {
    if (c.parentId) repliesByParent.set(c.parentId, [...(repliesByParent.get(c.parentId) ?? []), c]);
  }
  let pins = 0;
  return sorted
    .filter((c) => !c.parentId)
    .map((root) => ({
      root,
      replies: repliesByParent.get(root.id) ?? [],
      pinNumber: root.page != null ? ++pins : null,
    }));
}

/** The pins to draw for one revision: pinned threads created on it. */
export function pinsForRevision(comments: ReviewComment[], revisionNo: number): Pin[] {
  return buildThreads(comments)
    .filter((t) => t.pinNumber != null && t.root.revisionNo === revisionNo)
    .map((t) => ({
      id: t.root.id,
      page: t.root.page as number,
      x: t.root.x as number,
      y: t.root.y as number,
      label: String(t.pinNumber),
      resolved: t.root.resolvedAt != null,
    }));
}

/** Brings a thread's card into view (and does nothing where scrolling is unavailable). */
export function scrollToThread(id: string) {
  document.querySelector(`[data-thread-id="${id}"]`)?.scrollIntoView?.({ behavior: "smooth", block: "nearest" });
}
