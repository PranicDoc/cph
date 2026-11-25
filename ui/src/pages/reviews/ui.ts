/**
 * Layout classes shared by the review screens. Each element carries the new view's class (the `.hlx`
 * rules in ui-new/styles, which win there) plus token-based utilities for the classic view.
 */
export const PAGE = "hlx mx-auto flex w-full max-w-6xl flex-col gap-4 p-4 [html[data-ui=new]_&]:p-0";

// Not `bg-card`: the new view's light theme re-tints that class and would beat `.surface`.
export const SURFACE = "surface rounded-xl border bg-[var(--color-card)] [html[data-ui=new]_&]:border-0";

/** A status pill in a page header, which in the new view's light theme sits on the violet ground. */
export const HEAD_BADGE = "[html[data-ui=new]_&]:![background:var(--glass-strong)]";

/** Warning tones use the new view's tokens, falling back to the classic amber. */
export const WARN_BOX =
  "rounded-xl border p-3 text-sm [background:var(--warn-soft,var(--color-amber-50))] " +
  "border-[color:var(--warn,var(--color-amber-300))] text-[color:var(--warn,var(--color-amber-900))]";

/** A table that turns into one card per row under 760px. */
export const TABLE = {
  table: "t t-actions-last w-full text-left text-sm max-[760px]:block",
  head: "max-[760px]:hidden",
  th: "px-4 py-3 text-xs font-semibold text-muted-foreground",
  body: "max-[760px]:block",
  row: "border-t max-[760px]:block max-[760px]:px-4 max-[760px]:py-3 max-[760px]:first:border-t-0",
  title: "px-4 py-3 align-middle max-[760px]:block max-[760px]:p-0 max-[760px]:pb-2",
  cell:
    "px-4 py-3 align-middle max-[760px]:flex max-[760px]:items-center max-[760px]:justify-between " +
    "max-[760px]:gap-3 max-[760px]:px-0 max-[760px]:py-1 max-[760px]:before:text-xs " +
    "max-[760px]:before:text-muted-foreground max-[760px]:before:content-[attr(data-label)]",
  actions: "px-4 py-3 text-right align-middle max-[760px]:flex max-[760px]:justify-end max-[760px]:px-0 max-[760px]:pb-0",
};

/** "Today, 10:42", "Yesterday, 17:05", otherwise "20 Nov, 09:18" (with the year when it is not this one). */
export function formatWhen(iso: string | null): string {
  if (!iso) return "";
  const date = new Date(iso);
  const now = new Date();
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
  let day: string;
  if (date.toDateString() === now.toDateString()) day = "Today";
  else if (date.toDateString() === yesterday.toDateString()) day = "Yesterday";
  else
    day = date.toLocaleDateString(undefined, {
      day: "numeric",
      month: "short",
      ...(date.getFullYear() === now.getFullYear() ? {} : { year: "numeric" }),
    });
  return `${day}, ${date.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", hour12: false })}`;
}
