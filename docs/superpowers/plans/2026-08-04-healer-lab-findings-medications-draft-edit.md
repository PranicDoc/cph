# Healer Lab Findings & Medications DRAFT-Edit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a healer upload lab findings and add/edit medications on the "Start Session" screen while the case is still `DRAFT`, and keep both fields read-only once the case is `ACTIVE`/`CLOSED`.

**Architecture:** Pure frontend change. `buildSessionSchema()` (`cph/ui/src/pages/schemas/sessionSchema.ts`) gains an `isDraft: boolean` parameter that drives `readOnly` on the two affected field configs, replacing the current hardcoded `readOnly: true`. `Session.tsx` passes `sessionData?.status === "DRAFT"` into it via the existing `useMemo`. No backend changes — `CaseService.updateCaseDetails` already persists both fields unconditionally for non-patient callers.

**Tech Stack:** React + TypeScript, TanStack Form, Vite. No new dependencies.

## Global Constraints

- No backend changes — confirmed `CaseService.updateCaseDetails` (`cph/app/src/main/java/com/pranicdoc/cph/healing/service/impl/CaseService.java:411-430` for labFindings, `:382-389` for medications) already accepts and persists both fields for any non-patient caller.
- Editability must be driven by the case's persisted `status` field (`"DRAFT"`), not the page's general `isEdit` toggle state — re-opening an already-`ACTIVE` case and clicking the edit pencil must NOT unlock these two fields.
- Verify with `npx tsc --noEmit` and `npx vite build` from `cph/ui` before committing (this repo has no component-level test suite for schema/page files of this kind — matches existing test depth for comparable UI changes).

---

### Task 1: Make labFindings/medications editability follow case DRAFT status

**Files:**
- Modify: `cph/ui/src/pages/schemas/sessionSchema.ts:14-32` (function signature + labFindings field) and `:52-82` (medications field)
- Modify: `cph/ui/src/pages/Session.tsx:72` (schema `useMemo`)

**Interfaces:**
- Consumes: `sessionData.status` (string, already fetched into component state in `Session.tsx` — existing field, no new fetch needed) and `Protocol[]` (existing `protocols` from `useProtocols("PROTOCOL")`, unchanged).
- Produces: `buildSessionSchema(protocols: Protocol[], isDraft: boolean): FormSchema` — the new second parameter. Any other future caller of `buildSessionSchema` must be updated to pass this argument (there is currently only one call site, in `Session.tsx`).

- [ ] **Step 1: Update the schema function signature and the two field configs**

In `cph/ui/src/pages/schemas/sessionSchema.ts`, change:

```ts
export function buildSessionSchema(protocols: Protocol[]): FormSchema {
```

to:

```ts
export function buildSessionSchema(protocols: Protocol[], isDraft: boolean): FormSchema {
```

Then change the `labFindings` field config from:

```ts
      {
        name: "labFindings",
        label: "Lab Findings",
        type: "file",
        readOnly: true,
      },
```

to:

```ts
      {
        name: "labFindings",
        label: "Lab Findings",
        type: "file",
        readOnly: !isDraft,
      },
```

And the `medications` field config from:

```ts
      {
        name: "medications",
        label: "Medications",
        type: "table",
        readOnly: true,
        displayKey: "medicationName",
```

to:

```ts
      {
        name: "medications",
        label: "Medications",
        type: "table",
        readOnly: !isDraft,
        displayKey: "medicationName",
```

Leave every other field and line in the file unchanged.

- [ ] **Step 2: Update the call site in Session.tsx**

In `cph/ui/src/pages/Session.tsx`, change:

```ts
  const schema = useMemo(() => buildSessionSchema(protocols), [protocols]);
```

to:

```ts
  const schema = useMemo(
    () => buildSessionSchema(protocols, sessionData?.status === "DRAFT"),
    [protocols, sessionData?.status]
  );
```

This line currently sits before `sessionData` is populated (it's fetched asynchronously in a `useEffect` below). That's fine — `sessionData` starts `null`/`undefined`, so `sessionData?.status === "DRAFT"` evaluates to `false` on the first render (fields read-only), and the `useMemo` recomputes automatically once the fetch completes and `sessionData.status` is set, since it's now in the dependency array.

- [ ] **Step 3: Type-check**

Run from `cph/ui`:

```bash
npx tsc --noEmit
```

Expected: no errors. If TypeScript flags `buildSessionSchema(protocols)` anywhere else in the codebase as missing an argument, that call site also needs the same treatment as Step 2 — check with:

```bash
grep -rn "buildSessionSchema(" cph/ui/src
```

Expected: only the one call site touched in Step 2.

- [ ] **Step 4: Production build**

Run from `cph/ui`:

```bash
npx vite build
```

Expected: build succeeds with no new errors (pre-existing chunk-size warning is unrelated and expected).

- [ ] **Step 5: Manual verification**

Two scenarios need checking in a browser against a real backend/DB (this repo has no automated test for this page):

1. Open the session screen for a `DRAFT` case (`/cases/:id` or wherever `Session.tsx` is routed from the dashboard's "Start" action). Confirm: the Lab Findings drag-and-drop upload box is interactive (not just a read-only file list), and the Medications table shows an "add" affordance / editable dosage-timing fields rather than a static list.
2. Open the session screen for an `ACTIVE` case. Confirm: Lab Findings shows the read-only download list (no upload box), Medications shows the read-only list. Then click the edit pencil to enable `isEdit` for the case's other fields (e.g. chief complaint) and confirm Lab Findings/Medications remain read-only even with `isEdit` true.

- [ ] **Step 6: Commit**

```bash
git add cph/ui/src/pages/schemas/sessionSchema.ts cph/ui/src/pages/Session.tsx
git commit -m "$(cat <<'EOF'
fix: let healers edit lab findings/medications only while a case is DRAFT

Both fields were hardcoded readOnly regardless of case status, even
though Session.tsx is healer-only and the backend already persists
them unconditionally for non-patient callers. Gate readOnly on the
case's actual status instead of the page's general edit toggle, so
re-opening an ACTIVE case and enabling edit mode for other fields
doesn't also unlock these two.
EOF
)"
```

---

## Self-Review

**Spec coverage:** The spec's single requirement — DRAFT-only editability for `labFindings` and `medications`, gated on case status not the general edit toggle, no backend changes — is fully covered by Task 1, Steps 1–2. Testing requirements (type-check, build, manual DRAFT/ACTIVE verification) are covered by Steps 3–5.

**Placeholder scan:** No TBD/TODO markers; every step shows exact before/after code.

**Type consistency:** `buildSessionSchema(protocols: Protocol[], isDraft: boolean): FormSchema` is used identically in both the definition (Step 1) and the call site (Step 2). `sessionData?.status` matches the existing type already used elsewhere in `Session.tsx` (e.g. line 202's `data.status === "DRAFT"`, line 284's `sessionData.status === "DRAFT"`).
