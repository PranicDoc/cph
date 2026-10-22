# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
- **Healers (primary customers):** independent energy-healing practitioners who document each client's case: patient registration, consent declaration, chakra scans, sessions, protocols, closure, and a generated case PDF. They work between or during client sessions, often on a phone or laptop, and want documentation to stay out of the way of the healing itself.
- **Admin / ops team (this redesign starts here):** a growing team of staff (super-admin plus support/ops with limited roles) who manage credit packages and plans, healer accounts and transactions, roles and permissions, master data (protocols, chakras, colours), legal documents, global config, and healer invites.
- **Patients:** register themselves through a healer's link and sign the declaration; they see a short form, not the admin or healer app.

## Product Purpose
HealingARK (formerly PranicDoc) is a documentation platform for healers: it replaces paper case sheets with a guided digital flow and produces a professional case PDF. Success: a healer documents a case without worry, the record is complete and trustworthy, and the operating team can run the platform (plans, accounts, access, content) without engineering.

## Positioning
A calm, professional record-keeping tool built specifically for healing practice (chakra scans per session, protocols from the source books, consent and declaration, case PDF), rather than a generic clinic or note-taking app. The product speaks to healers as a community ("you are part of the HealingARK community; we will keep making this simpler").

## Operating Context
- Web app (React 19, Vite, Tailwind v4, shadcn/Radix). Light and dark mode are both required.
- Mobile web matters (healers on phones); admin tables already have mobile card layouts.
- Credit-based plans (free, starter, pro, enterprise) with validity periods; Packages, Accounts and Transactions are core admin screens.
- Roles and permissions are fine-grained (resource + verb grants), edited in a Roles form with a review step.

## Capabilities and Constraints
- Visual redesign only: no behaviour or API change.
- Existing violet brand palette stays (primary #6C3FC5); the real logo is not available yet, so a placeholder mark and the wordmark "HealingARK" are used until it arrives.
- Brand name is **HealingARK** in all user-facing text; "PranicDoc" must not appear. "Pranic Healing" remains only as the name of the modality.
- Admin screens first; healer screens follow once the admin look is approved. Playwright coverage is a later step.
- Undecided: final logo, dark-mode default (proposal: follow the system).

## Brand Commitments
- Name: HealingARK.
- Feel (confirmed): calm, trustworthy, premium.
- Binding visual direction from the owner: an Apple-style premium glass look, with Cal.com-level clarity in forms.

## Evidence on Hand
Real admin screens exist (Packages, Accounts, Transactions, Roles + Role form, Global Config, Legal Documents, Healer Registration, Master Data). No final logo, no photography, no testimonials: none may be invented.

## Product Principles
1. Calm over clever: the interface lowers worry; nothing shouts.
2. Clarity before ornament: forms and tables are instantly legible; glass is a material, not decoration.
3. One system, two modes: light and dark share tokens and feel equally finished.
4. Trust is visible in the details: precise spacing, honest states, no fabricated claims.
5. Built for a team: dense data stays scannable; permissions and destructive actions are unmistakable.

## Accessibility & Inclusion
Text over glass must meet WCAG AA contrast in both modes; visible keyboard focus; reduced-motion and reduced-transparency respected; usable at phone width.
