# HealingARK UI redesign — plan (admin screens first)

Status 2026-10-20. Notion: "UI redesign: classy, modern, glassy professional look for HealingARK". Visual only, no behaviour change.

## Decisions from the owner
- **Colours:** keep the existing palette (violet brand). Logo comes later: build a `BrandMark` placeholder so it is a one-file swap.
- **Modes:** light and dark.
- **Order:** admin screens first; user (healer) screens after the look is agreed on admin.
- **Playwright:** later.
- **References:** none yet; candidates below for the owner to review.

## What exists today
- React 19, Vite, Tailwind v4 (`@theme` tokens in `ui/src/index.css`), shadcn/Radix components, Segoe UI font.
- Palette: primary `#6C3FC5` (light `#8B6AD6`, dark `#4E2A9E`), background `#F7F5FB`, card `#FFFFFF`, text `#2D2B35` / `#6B6880`, border `#E4E0ED`; success `#34C759`, warning `#FF9500`, danger `#FF3B30`; chakra colours; brand gold `#E8A838`, teal `#2EC4B6`. No dark mode tokens yet.
- Admin screens (`ui/src/pages/admin`): AdminLayout shell, Accounts, Transactions, Packages, Global Config, Master Data, Legal Documents, Roles + Role form, Healer Registration (invites).

## Design direction (proposal, to be shown as a mockup before any code)
- **Glass material:** translucent surfaces with backdrop blur, 1 px hairline border (white 40% in light, white 10% in dark), soft layered shadow, optional specular top edge. Used on shell, cards, dialogs, popovers. Never on dense table bodies (readability + performance).
- **Background:** calm gradient / soft "aurora" blobs in the brand violet (+ gold/teal accents at low opacity), so the glass has something to refract.
- **Type:** a modern variable sans (Inter or Geist) instead of Segoe UI; tighter hierarchy (page title, section, label, data), tabular numbers in tables.
- **Shape and motion:** 12-16 px radii, subtle 150-200 ms transitions, reduced-motion respected.
- **Dark mode:** same hue family, deep violet-black background, glass at lower opacity; semantic tokens swap via `data-theme`, default follows the system, toggle in the top bar, choice remembered.
- **Logo:** `BrandMark` component (placeholder mark + "HealingARK" wordmark), used in the shell and login.

## Technical approach
1. **Tokens first:** semantic tokens (surface, surface-glass, border-glass, text, muted, brand, ring, shadow) defined for light and dark in `index.css`; existing `--color-*` names stay as aliases so nothing breaks.
2. **Glass utilities:** `.glass`, `.glass-strong`, `.glass-subtle` classes; fallback to solid surface when `backdrop-filter` is unsupported or `prefers-reduced-transparency`.
3. **Shared components restyled once** (button, input/select, card, badge, dialog/alert-dialog, dropdown/popover, table/data-table, tabs, switch, toast): pages inherit the look.
4. **Admin shell** (AdminLayout: sidebar + top bar + theme toggle + BrandMark), then pages.
5. **Theme toggle** hook (`useTheme`), no flash of wrong theme (inline script sets `data-theme` before paint).

## Admin rollout (small PRs, each with before/after screenshots from Chrome)
1. Foundation PR: tokens, glass utilities, font, theme toggle, BrandMark placeholder, restyled shared components. No page changes yet except visual inheritance.
2. AdminLayout shell (sidebar, top bar, mobile drawer).
3. Packages + Accounts + Transactions (simple tables/forms).
4. Roles list + Role form (permission grid, review modal).
5. Global Config + Legal Documents + Healer Registration.
6. Master Data (largest: tabs, import, wipe dialogs).
After sign-off on the admin look: healer screens (login, dashboard, patients, case, session wizard, scan, PDFs) in the same pattern.

## Quality bar (every PR)
- Contrast: text over glass meets WCAG AA (4.5:1); verified in both modes.
- Responsive down to phone width (mobile web is a Nov goal); admin tables keep their mobile card layouts.
- Performance: at most one blurred layer per region, no blur on large lists; checked on a throttled low-end profile.
- Keyboard focus visible on all controls; reduced-motion honoured.
- `tsc`, vitest green; no behaviour or API change.

## Reference candidates for the owner to review
(From my own knowledge, not freshly browsed; I can screenshot any of them in Chrome for a side-by-side mood board.)
| Site | Take from it |
|---|---|
| linear.app | Restraint, craft in dark mode, dense but calm tables |
| stripe.com (dashboard look) | Type hierarchy, subtle gradients, trustworthy data screens |
| vercel.com (Geist) | Clean monochrome base, light glass navigation |
| apple.com / visionOS | The real "glass" material: blur, vibrancy, specular edge |
| raycast.com | Dark translucent panels, polished overlays and menus |
| mercury.com | Calm, premium admin feel for a regulated domain |
| attio.com | Modern record/table views, relevant to patients and cases |
| cal.com | Clean forms and settings pages |

## Skills
The impeccable and taste design skills are not installed. Candidates found: `pbakaus/impeccable` (impeccable), and for taste several (`nexu-io/open-design@taste-skill`, `cristicretu/family-taste-skill@design-with-taste`, `codeswithroh/tastemaker`). Owner to confirm which ones to install before use.

## Open items
- Logo and final brand tweaks (later).
- Which references the owner likes; direction mockup approval before code.
- Dark-mode default: follow system (proposed).
