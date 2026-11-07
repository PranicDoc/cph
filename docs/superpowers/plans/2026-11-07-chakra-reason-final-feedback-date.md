# Chakra Reason + Editable Final Feedback Date Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** (1) The healer sets the final feedback date on the manual feedback page (default: last session date) and the PDF prints it; (2) the new view's "Close case" opens a full protocol-understanding page with an optional 300-char "why this chakra" box per chakra, printed under the chakra name in the PDF; (3) the protocol colour pre-fill matches by protocol name + book (pre-fill stays switched off in the UI).

**Architecture:** No new date field: the final `QualitativeFeedback.healingDate` becomes user-set and the PDF mapper prefers it. Reasons ride in one new JSON column on `healer_protocol_understanding` and one new `chakraReasons` map on the common-dtos snapshot. UI: the step-1 loading/saving logic moves out of `ClosureForm` into a hook shared by the old-view dialog and the new page.

**Tech Stack:** Spring Boot 3.3 / Java 17 / Flyway / MapStruct (cph, pdfWorker), common-dtos (java-library), React + TanStack Query + Vitest (cph/ui).

**Spec:** the agreed chat design (2026-11-07); decisions are copied into Global Constraints.

## Global Constraints

- Keep changes minimal; no refactors beyond the hook extraction.
- New view only for UI changes; the old view keeps working as today (its dialog still has step 1).
- Final feedback date: no future dates (UI `max` = today, backend `@PastOrPresent`); null → latest session date; editable after closure (only LOCKED blocks, as today).
- Reason: one per chakra, optional, max **300** chars, trimmed, blank = absent.
- Pre-fill: UI call stays commented out (#144). Backend pre-fill returns colours only, **properties empty** (healer picks them).
- common-dtos version **0.1.7**; cph and pdfWorker depend on it. CI publish has a 401 issue: user publishes; locally use `publishToMavenLocal`.
- Migration **V53**. Branches: cph `feat/hpu-reason-final-date`, pdfWorker/common-dtos `feat/hpu-chakra-reason` (worktrees under `E:\PranicDoc\PranicDoc\worktrees\*-hpu-reason`). Windows: `gradlew.bat`.
- User-facing text says HealingARK, never PranicDoc.

## Review Focus

- Editing an existing final feedback without touching the date must keep its stored date (not reset to session date) — test in Task 2.
- A final feedback recorded via WhatsApp (dated with the session) still prints in the PDF — Task 2 mapper test.
- Old HPU rows with `chakra_reasons_json = NULL` load and print with no reason — Task 4 test.
- A chakra name + 300-char reason fits the 150×36pt cell (font shrinks below 5.5pt for protocol cells) — Task 7 layout test.
- Two catalog protocols share a name across books: only the one whose page is in the case's matching book list contributes colours — Task 5 test.

---

### Task 1: common-dtos — `chakraReasons` on the HPU snapshot (0.1.7)

**Files:** Modify `common-dtos/src/main/java/com/pranicdoc/common/dto/HealerProtocolUnderstandingSnapshot.java`, `common-dtos/build.gradle`

**Interfaces — Produces:** `HealerProtocolUnderstandingSnapshot(..., List<ChakraReadingSummarySnapshot> chakraReadings, Map<String,String> chakraReasons)` (new last component; chakra name → reason).

- [ ] Add the component; `version = '0.1.7'`.
- [ ] `gradlew.bat build publishToMavenLocal` → BUILD SUCCESSFUL.
- [ ] Commit `feat(dto): HPU snapshot carries per-chakra reasons (0.1.7)`.

### Task 2: cph — final feedback date (backend)

**Files:** Modify `healing/dto/ManualFeedbackRequest.java`, `healing/dto/CaseFeedbackResponse.java`, `healing/service/impl/FeedbackService.java`, `healing/mapper/CphCaseMapper.java`. Test: `FeedbackServiceManualTest.java`, `CphCaseMapperFeedbackTest.java`.

**Interfaces — Produces:** request field `@PastOrPresent LocalDate finalFeedbackDate` (nullable, last component; replaces the uncommitted `String` version on `fix/case-edit-symptom-null-parent`); response `CaseFeedbackResponse(interims, finalFeedbackText, LocalDate finalFeedbackDate)`.

- [ ] Tests (fail first): `finalSavedWithChosenDate` (new final → `healingDate == chosen`); `finalDefaultsToLatestSessionDate` (null → latest session date); `editKeepsDateWhenNoneSent` and `editUpdatesDateWhenSent`; `getManualFeedbackReturnsFinalDate`; mapper `lastFeedbackDatePrefersFinalFeedbackDate` (interim later than final → final's date printed).
- [ ] Implement: `saveManualFinal(caseId, symptoms, text, date)` — new: `date ?? latest.getSessionDate()`; edit: set `healingDate` only when `date != null`. `getManualFeedback` returns newest final's `healingDate`. `latestFeedbackDate(closure)`: the FINAL qualitative feedback's `healingDate` if any, else current max logic. Update the request's javadoc line "The healing date is never sent".
- [ ] `gradlew.bat test --tests "*FeedbackServiceManualTest" --tests "*CphCaseMapperFeedbackTest"` → PASS. Commit.

### Task 3: cph UI — editable final feedback date

**Files:** Modify `ui/src/pages/ManualFeedback.tsx`; Test `ui/src/pages/ManualFeedback.test.tsx` (create if absent, follow `Feedback.test.tsx` mocking style).

- [ ] Tests: final selected → date input enabled, `max` = today, value = `recorded.finalFeedbackDate ?? latestSession.sessionDate`; saving posts `finalFeedbackDate`; interim → input stays read-only with the session's date and no `finalFeedbackDate` sent.
- [ ] Implement with one `finalDate` state set in the existing "show what is recorded" effect; fix the "never typed" comments.
- [ ] `npx vitest run src/pages/ManualFeedback` → PASS. Commit.

### Task 4: cph — chakra reason (backend)

**Files:** Create `db/migration/V53__hpu_chakra_reasons.sql`; modify `healing/model/HealerProtocolUnderstanding.java`, `healing/dto/HealerProtocolUnderstandingRequest.java`, `common/validation/TextLimits.java`, `HealerProtocolUnderstandingService.java`, `CphCaseMapper.toHpuSnapshot`, `app/build.gradle` (common-dtos 0.1.7). Test: `HealerProtocolUnderstandingSaveTest.java`, `CphCaseMapperProtocolUnderstandingTest.java`.

**Interfaces — Produces:** `ChakraColorSelection(chakra, energisingColors, cleansingColors, @Size(max = TextLimits.CHAKRA_REASON) String reason)`; `TextLimits.CHAKRA_REASON = 300`; entity `chakraReasonsJson` (`chakra_reasons_json TEXT`); `hpuService.deserializeReasons(String json): Map<String,String>`.

- [ ] Migration: `ADD COLUMN chakra_reasons_json TEXT` on `healer_protocol_understanding` and `healer_protocol_understanding_aud`.
- [ ] Tests: save trims and stores non-blank reasons only; `getExistingSelection` returns them (null when absent); NULL column → empty map; snapshot carries `chakraReasons`.
- [ ] Implement; update every `new ChakraColorSelection(` call site (prefill passes `null`).
- [ ] `gradlew.bat test --tests "*HealerProtocolUnderstanding*" --tests "*CphCaseMapperProtocolUnderstandingTest"` → PASS. Commit.

### Task 5: cph — pre-fill by protocol name + book (UI flag stays off)

**Files:** Modify `HealerProtocolUnderstandingService.protocolColorsByChakra`, `toDefaultColors`. Test: `HealerProtocolUnderstandingPrefillTest.java`.

The case stores names (`HealingDetails.protocolName`, comma-joined) and pages per book (`HealingCase.basicBookPg/advancedBookPg/psychotherapyPg`, comma-joined), bucketed by the UI's `BOOK_FIELD_MAP` (`Session.tsx`). A catalog protocol counts only when its name matches **and** its `pageNumber` is in the page list of its book's bucket. Mirror the map as a private static `Map<String, Function<HealingCase,String>>` (default bucket: basic, as the UI does).

- [ ] Tests: `sameNameDifferentBook_onlyMatchingBookContributes`; `pageNotInCaseBookList_ignored`; `prefillLeavesPropertiesEmpty` (replaces the "all properties selected" assertion).
- [ ] Implement; `toDefaultColors` returns `new ColorSelection(name, List.of())`.
- [ ] Run prefill tests → PASS. Commit.

### Task 6: cph UI — protocol understanding page (new view)

**Files:** Create `ui/src/hooks/useProtocolUnderstanding.ts`, `ui/src/ui-new/pages/CloseCase.tsx`; modify `ui/src/interfaces.ts` (`HPUTable.reason?: string`), `ui/src/lib/textLimits.ts` (`chakraReason: 300`), `ui/src/components/closure/ClosureForm.tsx`, `ui/src/pages/Closure.tsx`, `ui/src/ui-new/closure/ProtocolUnderstanding.tsx`, `ui/src/App.tsx`, `ui/src/pages/Session.tsx`, `ui/src/pages/Patients.tsx`. Tests: `ui/src/ui-new/pages/CloseCase.test.tsx`; keep `ClosureForm*.test.tsx` green.

**Interfaces — Produces:** `useProtocolUnderstanding(healingCaseId) → { rows, ready, loading, updateRow, save /* useMutation */ }` (lifted verbatim from `ClosureForm`: chakras + existing fetch, merge, save POST; pre-fill lines stay commented). `ClosurePopup`/`ClosureForm` prop `skipUnderstanding?: boolean` (starts at step 2, left button is Cancel). Route `cases/:healingCaseId/close` with `location.state.patient`.

- [ ] Tests: page lists scanned chakras with saved colours and reasons; reason box enforces 300 (`maxLength` + `CharacterCount`); Save posts `reason` per chakra then opens the closure dialog without step 1; success navigates back.
- [ ] Implement: `ProtocolUnderstanding` adds an optional `Textarea` per open row ("Why this chakra? (optional)") via `onChange(index, "reason", v)`. `ClosureForm` uses the hook (old view unchanged in behaviour). New view: Session `onComplete`/`onEditClosure` and the Patients "Close" menu item `navigate(\`/cases/${id}/close\`, { state: { patient } })`; old view keeps the dialog.
- [ ] `npx vitest run` (full UI suite) and `npm run lint` → 0 failures / 0 errors; `npx tsc -b` clean. Commit.

### Task 7: pdfWorker — reason under the chakra name

**Files:** Modify `build.gradle` (common-dtos 0.1.7), `template/CaseSnapshotMapper.mapHealersProtocolUnderstanding(map, hpu)`, `service/impl/PdfRendererServiceImpl.drawMultiLineText`; fixtures that construct the snapshot. Test: `CaseSnapshotMapperTest.java`, `ProtocolTableLayoutTest.java`.

- [ ] Tests: `CHAKRA_REASON` = `"Ajna\nreason"` when a reason exists, `"Ajna"` when not / map null; layout: name + 300-char reason wraps within 36pt height.
- [ ] Implement: cell text `chakraName + "\n" + reason` when non-blank; protocol cells may shrink to `PROTOCOL_MIN_FONT_SIZE = 4f` (others keep 5.5f).
- [ ] `gradlew.bat test` → PASS. Commit.

### Finish

- [ ] Push branches only after the user's local test; user publishes common-dtos 0.1.7 first (merge order: common-dtos → cph → pdfWorker); V53 applies on deploy.
