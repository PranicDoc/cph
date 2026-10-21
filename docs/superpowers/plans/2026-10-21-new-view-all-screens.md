# New view: all remaining screens in one PR (tracker)

Owner decision 2026-10-21: instead of 8 more PRs, do ALL remaining screens in one PR: #54 (branch `feature/new-view-shells`, now on main; #53 merged). No role label under the user's name (done in #54).

Spec: `ui/design/DESIGN.md`. Mockups: `ui/design/mockups/index.html`. Plan: `2026-10-21-new-view-dual-ui.md`.

## Approach (keep classic untouched)
1. **Global reskin first (DONE: radius, glass panels, dialogs/menus/selects, form controls, tables with data-label + phone cards)** in `ui/src/ui-new/styles/new-view.css`, all scoped to `html[data-ui="new"]`: radius tokens (`--radius-*`), glass `bg-card` panels, dialogs/dropdowns/popovers as glass sheets (bottom sheets on phones), inputs/tabs/badges, tables with mobile card rows.
2. **Hook attributes, no visual change in classic**: add `data-hl="..."` / `data-label` to classic markup (for example `DataTable` cells get `data-label` from the column header) and style them in `new-view.css`.
3. **New components only where the structure changes** (use `versioned(Classic, loadNew)` from `ui-version`): session details two-column, patient banner, permissions picker, feedback slider, close-case protocol understanding.
4. Light mode: not-yet-adapted pages sit on the white glass `hl-canvas` panel (from the shells PR); adapted pages may drop it.

## Local run (for the owner and for verification)
- Servers (started from this worktree): UI http://localhost:3000 (`VITE_NEW_VIEW=on npm run dev`), cph backend :9918 (`bootRun --args=--spring.profiles.active=localdev` with dummy `R2_*` env), credit service :9112 (from `creditService`, same profile). Logs: `backend.log`, `credit.log`, `ui-dev.log` in the worktree root (untracked).
- Local-only test login for screenshots: `ui-test@example.com` (SUPADM, inserted into the local `pranic_doc` DB), password in `/tmp/uitest/cred.txt` (never commit or print it). Delete the row when done: `delete from healer where email='ui-test@example.com'`.
- Screenshots: headless Edge (`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`); the Chrome extension was not connected. For a logged-in view use playwright-core with `executablePath` of Edge (install outside the repo).

## Decisions still open (ask the owner)
- Feedback slider: higher = worse conflicts with the stored rating (emoji x 2, higher = better). Pick a mapping before building it (see DESIGN.md "Data note").
- Comments box on interim feedback links is not saved today (only on final links).
- Case notes need a backend field: separate story, not in this PR.

## Screens (mockup -> classic file) and status
| Screen | Classic file | Status |
|---|---|---|
| Shells (healer, admin, public, Legal Center) | `ui-new/shell/*` | done (#54) |
| Packages + edit sheet | `pages/admin/PackagesPage.tsx` | done (global reskin) |
| Accounts | `pages/admin/AccountsPage.tsx` | done |
| Transactions | `pages/admin/TransactionsPage.tsx` | done |
| Roles, Role form, many-permissions picker | `RolesPage.tsx`, `RoleFormPage.tsx` | done |
| Healer invites | `HealerRegistrationPage.tsx` | done |
| Master data | `MasterDataPage.tsx` (1,900 lines) | done |
| Legal documents | `LegalDocumentsPage.tsx` | done |
| Global config | `GlobalConfigPage.tsx` | done |
| Login | `pages/Login.tsx` | done |
| Dashboard | `pages/Dashboard.tsx` | done |
| Patients | `pages/Patients.tsx` | done |
| Session view / edit | `pages/Session.tsx`, `SessionHeader.tsx` | done |
| Wizard scans + voice + preview | `components/HealingSessionWizard/*` | done |
| PDFs | `pages/PdfList.tsx` | done |
| Profile | `pages/Profile.tsx` | done |
| Registration (4 steps) | `pages/Registration.tsx` | done |
| Case summary | `pages/CaseSummary.tsx` | done |
| Close-case dialog (protocol understanding) | `pages/Closure.tsx`, `components/closure/*` | done |
| Upgrade plan dialog | `components/PlanPicker.tsx` | done (global reskin) |
| Patient feedback (+ states) | `pages/Feedback.tsx` | done (emoji scale kept; slider deferred until the rating mapping is decided) |
| Health form | `pages/PatientHealthForm.tsx` | done |
| Legal Center | `pages/LegalCenter.tsx` | done |
| Accept invite, Sign-up | `AcceptInvite.tsx`, `Signup.tsx` | done |
| Error pages | `components/ErrorState.tsx` | done |
| Shared states (confirm, toasts, empty, skeleton) | `components/ui/*` | done |

Mark a row done only after it was looked at rendered (dark, light, 390 px) and `tsc --noEmit` + `vitest run` are green.
