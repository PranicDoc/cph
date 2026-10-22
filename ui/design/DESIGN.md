# HealingARK new view: design spec for development

Source of truth for the look: the standalone mockups in `design/mockups/` (open `index.html`; shared styles are in `design/mockups/hl.css`). This document explains how to build them. Where this document and a mockup disagree, the mockup wins; tell the designer.

The brand name shown to users is **HealingARK**, never "PranicDoc" or "HealingDoc". "Pranic Healing" as a modality name stays.

## 1. Principles

- Visual change only. No behaviour, API or data change in the new view. Every field, column, validation and permission rule that exists today stays.
- Calm, trustworthy, premium. Violet brand `#6C3FC5` stays. Apple-style glass on navigation and top layers; data surfaces are more opaque so text stays readable. Forms follow Cal.com clarity: label above, one column, helper text below, error text in the danger colour.
- Dark is the default theme of the new view. Light is fully supported. A third "system" choice follows the OS.
- Mobile is a first-class layout (390 px), not a squeezed desktop.
- Radii 10 to 16 px (24 px for sheets). No gradient text. No decorative icons without meaning.

## 2. Tokens

Define them as CSS variables on `html[data-ui="new"]` (light) and `html[data-ui="new"][data-theme="dark"]` (dark). Tailwind v4 `@theme` colours are CSS variables already (`--color-background` and so on); the new view overrides those same variables, so the existing shadcn components restyle without edits. Portals (dialogs, dropdowns) render on `body`, so the attributes must be on `html`, not on a wrapper.

| Token | Light | Dark |
|---|---|---|
| ground (page) | `#7B57CC` with `linear-gradient(160deg,#8A66DA,#7B57CC 50%,#6B45BE)` and soft glows | `#0C0914` with violet, blue and teal glows |
| glass (nav, topbar) | `rgba(255,255,255,.55)` | `rgba(30,23,48,.52)` |
| glass-strong (cards, tables, sheets) | `rgba(255,255,255,.80)` | `rgba(36,28,58,.78)` |
| text | `#1D1B26` | `#F3F1FA` |
| muted | `#5C5870` | `#B5B0CB` |
| accent text | `#5B30B0` | `#C2ABFF` |
| primary | `#6C3FC5` (hover `#5E34B3`) | `#7C4DE0` (hover `#8C61EA`) |
| primary soft | `rgba(108,63,197,.14)` | `rgba(150,110,240,.18)` |
| hairline | `rgba(45,43,53,.10)` (strong `.18`) | `rgba(255,255,255,.09)` (strong `.18`) |
| field | `rgba(255,255,255,.92)` | `rgba(255,255,255,.06)` |
| success / warn / danger | `#17703D` / `#9A5200` / `#C4271B` | `#5BD98A` / `#FFB454` / `#FF8A80` |
| ring (focus) | `rgba(108,63,197,.34)` | `rgba(170,135,255,.45)` |

Light-mode exception: the sidebar is deep violet glass (`linear-gradient(180deg, rgba(68,36,140,.84), rgba(46,24,102,.86))`, 34 px blur, 1 px inner highlight on the top edge, two-layer shadow) with white text. Text that sits directly on the violet ground (page title, subtitle, pager text, segmented-filter labels) is white.

Typography: Inter (already installed as `@fontsource-variable/inter`), 14 px body, 28 px page title (24 px on mobile), weights 400, 500, 600, 650 for emphasis. Tabular numbers for credits, prices, dates and counts.

## 3. Materials

- `glass`: translucent fill, `backdrop-filter: blur(30px) saturate(1.7)`, hairline highlight, soft shadow. Used for the sidebar, topbar, mobile header and tab bar.
- `surface`: glass-strong, blur 18 px, radius 16. Used for cards and tables.
- `sheet`: glass-strong, blur 40 px, radius 24, large shadow. Used for dialogs and drawers.
- Fallbacks are required: `@supports not (backdrop-filter: blur(1px))` and `@media (prefers-reduced-transparency: reduce)` switch to opaque fills. At most one blur layer per region, and no blur on large scrolling tables.

## 4. Components (class names in `hl.css` are reference only)

- **Buttons**: primary (violet, inner top highlight), secondary (field fill, strong hairline), ghost, danger (text only). 40 px high, 10 px radius, never wrap.
- **Input / select / textarea**: 40 px, 10 px radius, strong hairline, focus ring 3 px. Label above (13 px, weight 550), helper text 12.5 px muted below, error 12.5 px danger.
- **Switch**: 40 x 24, primary when on.
- **Badge**: 24 px pill; neutral, ok, warn, off. **Chip**: 28 px pill, selectable (`on` state). Both single line.
- **Segmented filter** (All, Healer, Clinic): equal-width segments filling the control; on mobile the control is full width.
- **Tabs**: a glass pill row; on mobile an even 2-column grid.
- **Table**: desktop table inside a `surface`. Below 760 px each row becomes a card: first column is the title, other columns are "label : value" rows (use `data-label` on cells), row actions move to the card's top-right corner.
- **KPI card**: label, 30 px value, delta line.
- **Level tags** (chakra scans): energy `D N C`, activation `U N O`, 38 px wide round chips, selected one filled with primary soft. A one-line legend sits under the table.
- **Protocol row**: name, book name (single line, truncates), page badge on the same row. The page and book come from the protocol master data; there are no separate book-page inputs.
- **Patient banner** (session screens): avatar, "Healing session, name", age, gender, session number, last visit, condition chips, status badge. Segments of the sub line never break in the middle.
- **Stepper / progress**: "Step 1 of 2" with two bar segments.
- **Permissions picker** (many permissions): search, "n of N selected", Select all / Clear, a group list with per-group counts (8/12), the selected group's permissions in two columns with one-line descriptions, "Select all in group". On mobile the group list is a horizontal strip above a single column.
- **Protocol understanding** (close-case dialog, step 1): one accordion row per scanned chakra (name, "Filled in" or "Not filled" badge, a one-line summary when collapsed). Opened, it shows two panels, Energising colours and Cleansing colours. Each panel has a colour multi-select (chips with a swatch), and under it one group per selected colour listing that colour's properties as toggle chips ("2 of 3 properties"). The data is unchanged from today: per chakra and per colour type, a list of colours each with its selected properties (today a flat "Colour: Property" multi-select). Properties come from the colour master data. Dialog is 1040 px wide on desktop, a bottom sheet on mobile.
- **Severity slider** (patient feedback): one 0 to 10 slider per symptom, higher means a stronger symptom. 34 px thumb showing the number, track fill shifting green to amber to red, end labels "0 None" and "10 Worst", a badge with the number and a band (0 None, 1-3 Mild, 4-6 Moderate, 7-8 Severe, 9-10 Worst). Not rated yet is a hollow dashed thumb; the page still needs at least one symptom rated. Build it on a real `input type="range"` (keyboard and screen reader support) and style it. **Data note:** the app stores today's emoji rating as 2, 4, 6, 8, 10 where higher means better. A "higher is worse" slider flips that meaning, so the mapping to the stored rating (and anything reading it: reports, case summary "Better / Same", PDFs) must be decided before this is built.
- **Case notes** card: textarea, "Saved just now", Dictate button with a Beta badge. New feature in the new view; needs a backend field before it can be wired (out of scope of the visual work).

## 5. Shell

- **Desktop**: floating glass sidebar (244 px, 14 px margin, radius 20) with brand mark, grouped nav and the user at the bottom; glass topbar with breadcrumb, search and theme toggle; content max width fluid.
- **Admin nav**: Accounts, Transactions, Packages, then Access (Roles, Healer invites), then Content (Master data, Legal documents, Global config), filtered by role exactly as today (`adminNavItems`).
- **Healer nav**: Dashboard, Patients, PDFs, Profile (`healerNavItems`).
- **Mobile (below 760 px)**: sidebar and topbar hide. A glass header (brand mark, page title, theme toggle, avatar) sits at the top; a floating glass tab bar follows the content. Admin tabs: Accounts, Packages, Roles, Content, Config. Healer tabs: Dashboard, Patients, PDFs, Profile. The tab bar is sticky and must not cover the last item (reserve space under the content).
- **Logo**: a placeholder `BrandMark` component until the real logo is supplied.

## 6. Screen inventory

Mockup file = `design/mockups/screens/<Name>.<light|dark>.html`; every screen has light, dark and a mobile layout (the same file under 760 px).

| Mockup | Route | Today's component |
|---|---|---|
| Packages, PackageForm | `/admin/packages` | `pages/admin/PackagesPage.tsx` |
| Accounts | `/admin/accounts` | `AccountsPage.tsx` |
| Transactions | `/admin/accounts/:ownerType/:ownerId/transactions` | `TransactionsPage.tsx` |
| Roles, RoleForm, RoleFormMany | `/admin/roles`, `/new`, `/:id/edit` | `RolesPage.tsx`, `RoleFormPage.tsx` |
| Invites | `/admin/healer-registration` | `HealerRegistrationPage.tsx` |
| MasterData | `/admin/master-data` | `MasterDataPage.tsx` |
| Legal | `/admin/legal-documents` | `LegalDocumentsPage.tsx` |
| GlobalConfig | `/admin/global-config` | `GlobalConfigPage.tsx` |
| Login | `/login` | `pages/Login.tsx` |
| Dashboard | `/` | `pages/Dashboard.tsx` |
| Patients | `/patients` | `pages/Patients.tsx` |
| SessionView, SessionEdit | `/session/:healingCaseId` (view and edit mode) | `pages/Session.tsx`, `SessionHeader.tsx` |
| SessionScan, SessionPostScan | wizard steps | `components/HealingSessionWizard/*` |
| PdfList | `/pdf-list` | `pages/PdfList.tsx` |
| Profile | `/profile` | `pages/Profile.tsx` |
| ProfileMenu | user dropdown in both shells | `components/UserProfile/UserProfileIcon.tsx` |
| VoiceScan, ScanPreview | wizard scan step, Voice mode and the preview dialog | `ScanStep.tsx`, `VoiceRecorder.tsx`, `ScanPreviewDialog.tsx` |
| RegPersonal, RegHistory, RegConsent, RegDone | `/registration` (healer registers a patient; `/self-register` uses the same steps in the public shell) | `pages/Registration.tsx`, `pages/schemas/RegistrationSchemas.ts` |
| CaseSummary | `/cases/:healingCaseId/summary` | `pages/CaseSummary.tsx` |
| ClosureColors, ClosureFinal | close-case dialog, steps 1 and 2 | `pages/Closure.tsx`, `components/closure/ClosureForm.tsx` |
| UpgradePlan | dialog from the profile menu | `components/PlanPicker.tsx`, `UserProfileIcon.tsx` |
| Feedback, FeedbackLoading, FeedbackNeedsRating, FeedbackSubmitError, FeedbackThanks, FeedbackExpired | `/feedback` (patient, public; reached from the WhatsApp link, so phone first) | `pages/Feedback.tsx`, `components/ErrorState.tsx` (`expired` variant) |
| HealthForm | `/patient-health/:caseId` (patient, public) | `pages/PatientHealthForm.tsx` |
| LegalCenter | `/legal-center` | `pages/LegalCenter.tsx`, `components/legal/*` |
| AcceptInvite, Signup, SignupSent | `/accept-invite`, `/signup` | `pages/AcceptInvite.tsx`, `pages/Signup.tsx` |
| NotFound, Forbidden, InviteExpired | error states (also the expired feedback link) | `components/ErrorState.tsx` |
| States | confirm dialog, toasts, empty, loading, inline error | shared `ui/*`, `sonner` |

All user-facing screens that exist today now have a mockup. The public pages (`PublicShell`, Legal Center shell, centred cards) use a plain glass header with the brand mark and no sidebar. Dialogs are centred glass sheets on desktop and bottom sheets on mobile. The healer declaration on registration (a checkbox when the healer registers on the patient's behalf, patient signature filled in and read-only) is shown as it works today.

**Profile menu and the view switch**: a "Try the new view" item (classic) or "Switch to classic view" (new view) in the existing user menu, plus "Share feedback" and a Dark / Light / System choice in the new view. The preference is stored on the account.

## 7. Quality bar

- Text contrast at least 4.5:1 in both themes, including on glass over the ground (check the worst case: light cards on the violet ground, dark glass over the glow).
- Visible focus on every control; touch targets at least 40 px on mobile.
- Works at 390 px wide with no horizontal page scroll; wide tables never scroll the page.
- `prefers-reduced-motion` and `prefers-reduced-transparency` respected.
- Theme is applied before first paint (inline script in `index.html`) so there is no flash.
- Every change keeps `tsc --noEmit` and `vitest run` green and changes no behaviour.
