# design-sync notes — cph/ui

## Repo shape gotchas

- **cph/ui is an app, not a publishable library.** No `dist/`, no `build.lib`, no `main`/`module`/`exports` in `package.json`. The converter's synth-entry fallback is used instead of a real dist build.
- **`pkg: ".."` is a deliberate path trick**, not a typo. `--node-modules ./node_modules` (the real installed deps) + `pkg: ".."` makes the converter resolve `PKG_DIR = node_modules/.. = cph/ui` itself, so it treats the app's own source as "the package." This only affects internal path math — nothing is installed or renamed. If this ever breaks on a converter update, the fix is finding another way to point `PKG_DIR` at `cph/ui` while `--node-modules` still points at the real `node_modules` (needed for esbuild to resolve `react`, `radix-ui`, `lucide-react`, etc.).
- **`cssEntry` is NOT `src/index.css` directly.** The raw source only has `@import "tailwindcss"` + a `@theme` token block — no compiled utility classes exist until Tailwind's build runs. `cssEntry` points at `.design-sync/compiled-tailwind.css`, a copy of the real `npm run build` output (`dist/assets/index-*.css`, filename is content-hashed and changes every build — hence the `cp` in `buildCmd`). Always re-run `npm run build` (or the full `cfg.buildCmd`) before re-syncing if any Tailwind class usage changed, even if you don't think the CSS itself changed.

## Scoping (2026-08-18 first sync)

- **Only 4 components requested this round**: `Table`, `Button`, `Dialog`, `AlertDialog` (+ their sub-parts — 31 exported symbols total from these 4 files). The rest of `src/components/ui/` (accordion, avatar, badge, card, checkbox, command, data-table, drawer, dropdown-menu, input-group, input, label, multi-select, popover, select, separator, skeleton, slider, spinner, switch, textarea, tooltip) was deliberately deferred to a future re-sync.
- **Breadcrumb was requested but doesn't exist** in the codebase — dropped, not invented. Build it in the app first, then a future re-sync will pick it up.
- **Scoping mechanism**: there's no config field to include-by-glob, only `componentSrcMap` (per-symbol) or `srcDir` (whole directory). With ~90 total exported symbols across all files, per-symbol exclusion would've been a huge list, so instead `.design-sync/scope-src/` holds **copies** of just the 4 target files, and `cfg.srcDir` points there instead of `src/components/ui`.
  - **Re-sync risk**: `.design-sync/scope-src/*.tsx` are copies, not symlinks. If the real `src/components/ui/{table,button,dialog,alert-dialog}.tsx` change, `.design-sync/scope-src/` goes stale until manually re-copied. There's no automation for this yet.
  - **To add the rest of the components on a future re-sync**: simplest path is dropping `.design-sync/scope-src/` and pointing `cfg.srcDir` back at `src/components/ui` directly (the real, always-current directory) — the staging trick was only needed to shrink the FIRST upload's footprint. Existing previews/grades for Table/Button/Dialog/AlertDialog carry forward by name regardless.

## Verification skipped this round

- User declined the Playwright/Chromium install (~200MB), so:
  - `package-validate.mjs` ran with `--no-render-check` — previews are **not** machine-verified as rendering correctly.
  - `package-capture.mjs` (screenshot-based grading for the 4 authored previews) also needs Playwright and could not run — **no formal `.grade.json` verdicts exist for AlertDialog/Button/Dialog/Table.**
  - Instead: the bundle was served locally (`http-serve.mjs`) and the user did a manual eyeball review of `.review.html`, then approved upload. This is real but weaker than the tool's normal automated gate.
- **Re-sync risk**: if Playwright ever gets installed in this repo, re-run `package-capture.mjs --components AlertDialog,Button,Dialog,Table --out ./ds-bundle` to backfill real grades and validate with render-check on to confirm nothing is actually broken.

## Known render warns

- `[TOKENS_MISSING]`: 12 `--radix-*` custom properties (accordion content height, dropdown/select/popover/tooltip transform-origin and available-height, dropdown trigger width). These are set by Radix's own JS at runtime for animations — expected to be absent from static CSS. Not a real gap; don't chase this on future re-syncs.

## dtsPropsFor

Hand-written prop interfaces for the 4 root components (`Button`, `Table`, `Dialog`, `AlertDialog`) in `config.json` — auto-extraction produces `{[key: string]: unknown}` for everything in this repo because there are no named `<Name>Props` interfaces anywhere (shadcn convention inlines prop types on the function signature) and no real `.d.ts` tree to resolve them from (synth-entry mode). The 27 sub-component exports (DialogTrigger, TableHead, etc.) still get the weak catchall — acceptable for now since they're plain HTML-element passthroughs (`React.ComponentProps<"th">` etc.), but worth hand-typing too on a future pass if the design agent ever misuses them.
