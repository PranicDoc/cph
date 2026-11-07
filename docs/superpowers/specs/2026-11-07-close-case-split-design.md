# Close-case page: chakra list + detail (new view)

Date: 2026-11-07 · Status: approved by the owner (mockup "layout C", 2026-11-07)

## Why
The owner finds the current closing flow complicated: a long accordion of every scanned chakra
(reason + cleansing colours + energising colours + properties), then a popup with recommended
protocols, comments and "Is healing continued?". Healers have to feed a lot of data in one stretch.
Goal: a neat UI and a simple flow that is easy to fill.

## Goals
- One page, two columns, in the new view's existing look (light AND dark themes via the new view's
  CSS variables/classes in `ui/src/ui-new/styles/new-view.css`; no hard-coded colours).
- Left: a card listing every scanned chakra with a status badge (Done / To do), a one-line summary
  of its reason (or "Needs a reason"), a progress bar "N of M done", and a final "Wrap up" item.
- Right: only the selected item.
  - Chakra: required "Why did you work on this chakra?" (300 chars, counter) first; then cleansing
    and energising colours as swatch chips; properties behind an optional "+ Add properties" link.
    Footer: "← <previous>" and "Save & next: <next> →" (last chakra → Wrap up).
  - Wrap up: recommended protocols (supplementary protocol multi-select), comments, a
    "Healing will continue" switch with a one-line explanation, footer "← <last chakra>" and
    "Close case". The popup is not used in the new view any more.
- Header: title "Close case", patient name, a one-line instruction, and a "Close case" button that
  is enabled only when every chakra has a reason (it runs the same close as Wrap up).
- Phones: the columns stack — the list shows first; tapping an item opens its panel full width with
  a "Back to list" control.
- One property per colour in the UI: picking a property replaces the previous one. UI only — the API
  still accepts a list. A legacy record with several properties keeps showing them until the healer
  picks one.

## Saving (no API change)
- The server requires a non-blank reason on every chakra selection it receives (cph #151).
- "Save & next" POSTs `/api/v1/cases/{id}/protocol-understanding` with only the chakras that have a
  reason (trimmed non-blank), then moves on. Chakras without a reason stay as page state.
- "Close case" (Wrap up or header): saves the protocol understanding (all chakras — all have
  reasons by then), then POSTs `/api/v1/cases/{id}/close` with the same body the closure popup
  builds today (recommended-protocols note + comments, isHealingContinued), then refreshes
  `["patients"]` and navigates back, as `CloseCase` does now.
- Editing a closed/continued case: loads the existing understanding and closure (comments,
  protocols, continued flag) exactly as today; every chakra already has a reason.

## Non-goals
- No backend/API change. The old (deprecated) view keeps its dialog unchanged.
- The commented-out protocol colour pre-fill stays off.

## Constraints
- Minimal code; reuse `useProtocolUnderstanding` / `useProtocolColors`, the closure logic in
  `pages/Closure.tsx` (extract a small hook if needed so the popup and Wrap up share it — move code,
  don't rewrite), `ColorChipPicker`, `MultiSelect`, `Textarea`, `CharacterCount`, `TEXT_LIMITS`.
- User-facing text says HealingARK, never PranicDoc.

## Left to the builder
Exact component split, keyboard/focus handling, how the selected item is kept (state or URL hash),
and whether the progress bar and badges reuse existing new-view classes (prefer reuse).
