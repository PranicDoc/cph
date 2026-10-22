# New view (HealingARK redesign) alongside the classic view: plan

**Goal:** ship the redesigned UI as an opt-in "new view" that lives next to the existing UI, switchable from the user profile menu, so both can run at the same time until the new view is proven and the classic view is retired.

**Designs:** `cph/ui/design/mockups/index.html` (standalone mockups, light, dark, mobile). **Dev spec:** `cph/ui/design/DESIGN.md`.

**Status:** plan for review. No application code has been changed yet.

## 1. How the two views coexist

- **Same URLs, same data.** Both views use the same routes, hooks, API calls and permissions. Only the presentation differs, so deep links and bookmarks work in either view and nothing in the backend changes.
- **A `uiVersion` setting** with two values, `classic` (default) and `new`. A `UiVersionProvider` holds it and sets `data-ui` and `data-theme` on `<html>`. A small inline script in `index.html` applies both before first paint, so there is no flash.
- **Where the choice is applied:**
  - **Layout routes.** `Layout` and `AdminLayout` become thin selectors that render the new shell or the existing one.
  - **Pages.** Each redesigned page is registered with a `versioned(classicLoader, newLoader)` helper. A page that has no new version yet falls back to its classic component, wrapped in the new shell and reskinned by the tokens, so the new view is never broken or half-empty.
  - **Code splitting.** New-view code lives in `src/ui-new/` and loads lazily, so classic users do not download it.
- **Styling is isolated.** All new-view CSS is scoped to `html[data-ui="new"]`. It overrides the Tailwind `--color-*` variables, which restyles the shared shadcn components (buttons, inputs, dialogs, dropdowns) inside the new view for free. Classic visuals cannot change because none of the new rules apply to them. Portals render on `body`, which is why the attributes sit on `html`.
- **No copied business logic.** If a classic page mixes data handling and layout, the data part is extracted into a hook first (its own small PR with tests), then both views use it. The new view only supplies new markup.
- **The switch.** In the profile dropdown (`UserProfileIcon`, used by both shells): "Try the new view" (with a Beta badge) in classic; "Switch to classic view" in the new view. In the new view the menu also offers Theme: Dark, Light, System. A toast confirms the switch and says how to go back.
- **Safety.** A build flag `VITE_NEW_VIEW=off` hides the menu item and forces classic. If anything goes wrong in production we flip it and redeploy the UI image.

## 2. Rollout

1. **Opt-in beta**: default classic, anyone can try the new view. Collect feedback.
2. **Default flip**: once the screens are stable, new becomes the default and classic stays one click away.
3. **Retire classic**: delete the classic components and the selectors, move `ui-new` pages into place. Date to be decided after stage 2.

## 3. Work breakdown (one PR each, in order)

| # | PR | Contents | Size |
|---|---|---|---|
| 1 | Foundation | `UiVersionProvider`, `useUiVersion`, `versioned()`, token CSS for light and dark, glass utilities with fallbacks, theme init script, `BrandMark` placeholder, Inter, profile menu items, `VITE_NEW_VIEW` flag, tests | M |
| 2 | Shells | New admin and healer shells: glass sidebar, topbar, mobile header and tab bar, selectors in `Layout` / `AdminLayout` | M |
| 3 | Admin A | Packages (with the edit sheet), Accounts, Transactions | M |
| 4 | Admin B | Roles list, Role form with the many-permissions picker, Healer invites | M |
| 5 | Admin C | Master data (split the 1,800-line page into per-tab views), Legal documents, Global config | L |
| 6 | Healer A | Login, Dashboard, Patients, PDFs, Profile | L |
| 7 | Session details | Extract the data logic of `Session.tsx` into a hook; new view and edit screens (two columns, protocols, Marillete protocol) | L |
| 8 | Healing wizard | Pre and post scan with the D N C / U N O level tags | M |
| 9 | Remaining screens | After they are mocked: registration and declaration, case summary and closure, patient feedback and health form, Legal Center, Accept invite, Sign-up, Upgrade plan dialog, error pages | L |
| 10 | Hardening | Contrast and focus audit in both themes, 390 px pass on every screen, Playwright smoke for the switch and the main flows, performance check on a low-end phone, feedback review | M |

Each PR must keep `tsc --noEmit` and `vitest run` green, add tests for what it adds, and be checked in Chrome in both themes at desktop and 390 px. As agreed, you test locally before anything is pushed or merged.

## 4. Things that are not purely visual

- **Case notes card** (in the mockups): there is no case-notes field today. Building it needs a backend change (a notes column on the case, an endpoint, maybe including it in the PDF). Proposal: ship the new view without the card, and do notes as its own story afterwards (the Dictate button reuses the existing voice work).
- **Brand text**: the classic headers (`Layout.tsx`, `AdminLayout.tsx`, `PublicShell.tsx`) still say "HealingDoc". Proposal: fix that now in the classic view as a tiny separate PR, since the brand is HealingARK.
- **Protocol book pages**: the new screens read book and page from the protocol data, as designed. If any protocol lacks them in master data, the row shows the name only.

## 5. Risks and how we handle them

| Risk | Handling |
|---|---|
| Logic drifting between two versions | Shared hooks; both views covered by tests; classic is retired on a date |
| Bundle size | New view is lazy-loaded and split per page |
| Blur is slow on cheap phones | At most one blur layer per region, no blur on big tables, opaque fallbacks for `prefers-reduced-transparency` and no `backdrop-filter` |
| Radix dialogs and menus ignoring the theme | Theme attributes on `html`; token overrides, not wrapper classes |
| Big dynamic form on the session edit screen (`DynamicFormEngine`) | Reuse the engine and the schemas; only layout classes change per view |
| Screens we have not mocked | Fall back to classic inside the new shell until mocked |

## 6. Decisions (owner, 2026-10-21)

1. **Preference is stored per account**, so it follows the person across devices. PR 1 therefore has a small backend part: a `uiVersion` field on the healer profile (migration, read in the profile response, a `PUT` to change it) with the front end caching it in local storage so the first paint does not wait for the network. Admin users need the same field; check where admin profile data lives when PR 1 starts.
2. **Everyone sees "Try the new view"**, with the kill-switch flag as the safety net.
3. **Share feedback**: a menu item in the new view that opens a feedback channel for comments on the new look. Default until the owner says otherwise: a `mailto:hello@healingark.in` link with the subject "New view feedback". Easy to swap later for a form.
4. **Case notes are their own story**; the new view ships without that card, and the session mockups show it only as the planned place for it.
5. **All remaining screens are mocked before build starts.** Done: every user-facing screen has a mockup (see `DESIGN.md` section 6). Next step is the owner's review, then PR 1.
6. **Brand text fix**: separate tiny PR (cph #52) to change "HealingDoc" to "HealingARK" in the classic headers.
7. **New shells, toggle kept**: the new view gets its own shells; the classic UI stays until the toggle is removed in a later cleanup.

Updates to the table in section 3: PR 1 now includes the backend preference field; PR 9 is the registration, case summary, closure, upgrade dialog, public pages, Legal Center and error pages already mocked.
