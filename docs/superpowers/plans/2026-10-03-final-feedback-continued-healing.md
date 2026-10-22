# Final Feedback for `isHealingContinued` Closures — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send the patient a final-feedback request on every case closure (including `isHealingContinued = true`), keeping exactly one `FINAL` qualitative feedback per case that a later submission overwrites.

**Architecture:** `CaseService.closeCase` drops its `!isHealingContinued` guard and instead skips the request only when the latest session already has a `FINAL`. `FeedbackService.saveQualitativeFeedback` turns the FINAL "already submitted" throw into an upsert that updates text/remarks and re-points the row to the latest session. No schema, UI or pdfWorker change.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, Hibernate Envers, JUnit 5 + Mockito. Run Gradle with `gradlew.bat` (Windows).

**Spec:** `E:\PranicDoc\PranicDoc\docs\superpowers\specs\2026-10-02-final-feedback-continued-healing-design.md` (outer repo, commit `0bd5d4e`)

## Global Constraints

- Repo/module: `cph/app` only, backend only. Branch from `cph` `main` (use a worktree/feature branch, e.g. `feature/final-feedback-continued`); do not edit the checked-out `main`.
- No migrations, no new DB unique index, no change to `Feedback.tsx`, `ClosureForm.tsx`, pdfWorker, link expiry or `resendFeedback`.
- Exactly one `FINAL` `QualitativeFeedback` per case (service-layer invariant). `INTERIM` behaviour unchanged.
- `FeedbackAlreadySubmittedException` stays for symptom feedback; it is no longer thrown for `FINAL`.
- `markUsedBySessionId(...)` after a `FINAL` is unchanged.
- Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

- Re-close with no new session when the latest session already has a `FINAL` → no second request is sent (test in Task 1).
- Re-close after a new session was logged (latest session has no `FINAL`) → a request is sent (Task 1).
- Overwrite after a new session: the single row is re-pointed to the latest session so PDF healing number/date aren't stale (Task 2).
- Submitting `INTERIM` after a `FINAL` exists neither throws nor touches the `FINAL` (Task 2).
- First-ever `FINAL` still inserts a new row and marks the link used (Task 2).

---

### Task 1: Send final-feedback request on every closure

**Files:**
- Modify: `app/src/main/java/com/pranicdoc/cph/healing/repository/QualitativeFeedbackRepository.java`
- Modify: `app/src/main/java/com/pranicdoc/cph/healing/service/impl/CaseService.java` (field list ~line 64-85; `closeCase` ~line 333)
- Test: `app/src/test/java/com/pranicdoc/cph/healing/service/CaseServiceTest.java` (closeCase section ~line 480-565)

**Interfaces:**
- Produces: `boolean QualitativeFeedbackRepository.existsByHealingSessionIdAndFeedbackType(UUID sessionId, FeedbackType feedbackType)`
- Consumes: existing `NotificationService.sendFinalFeedbackNotification(HealingSession)`

- [ ] **Step 1: Update/add the failing tests**

In `CaseServiceTest.java` add the mock next to the other `@Mock` fields (after `notificationService`):

```java
    @Mock private com.pranicdoc.cph.healing.repository.QualitativeFeedbackRepository qualitativeFeedbackRepository;
```

Add import `com.pranicdoc.cph.common.enums.FeedbackType;` to the import block.

Replace the test `closeCase_whenHealingContinued_setsStatusContinuedAndSkipsFinalFeedbackNotification` with:

```java
    @Test
    void closeCase_whenHealingContinued_setsStatusContinuedAndSendsFinalFeedbackNotification() {
        UUID caseId = UUID.randomUUID();
        HealingCase healingCase = buildCaseForClosure(caseId);
        CaseClosureRequest request = new CaseClosureRequest("Feeling better, continuing treatment", true);

        caseService.closeCase(caseId, request);

        assertEquals(CaseStatus.CONTINUED, healingCase.getStatus(),
                "isHealingContinued=true must set status to CONTINUED, not CLOSED");
        assertTrue(healingCase.getClosure().getIsHealingContinued());
        verify(caseRepository).save(healingCase);
        verify(notificationService).sendFinalFeedbackNotification(healingCase.getSessions().get(0));
    }

    @Test
    void closeCase_whenLatestSessionAlreadyHasFinalFeedback_doesNotSendAnotherRequest() {
        UUID caseId = UUID.randomUUID();
        HealingCase healingCase = buildCaseForClosure(caseId);
        HealingSession last = healingCase.getSessions().get(0);
        last.setId(UUID.randomUUID());
        when(qualitativeFeedbackRepository
                .existsByHealingSessionIdAndFeedbackType(last.getId(), FeedbackType.FINAL))
                .thenReturn(true);

        caseService.closeCase(caseId, new CaseClosureRequest("Re-close, no new session", true));

        verify(notificationService, never()).sendFinalFeedbackNotification(any());
    }

    @Test
    void closeCase_afterNewSessionLogged_sendsRequestForNewLatestSession() {
        UUID caseId = UUID.randomUUID();
        HealingCase healingCase = buildCaseForClosure(caseId);
        HealingSession first = healingCase.getSessions().get(0);
        first.setId(UUID.randomUUID());
        HealingSession second = HealingSession.builder()
                .sessionNumber(2)
                .sessionDate(LocalDate.now())
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .build();
        second.setId(UUID.randomUUID());
        healingCase.getSessions().add(second);
        // first session has a FINAL from the earlier closure; the new latest one does not
        lenient().when(qualitativeFeedbackRepository
                .existsByHealingSessionIdAndFeedbackType(first.getId(), FeedbackType.FINAL))
                .thenReturn(true);
        when(qualitativeFeedbackRepository
                .existsByHealingSessionIdAndFeedbackType(second.getId(), FeedbackType.FINAL))
                .thenReturn(false);

        caseService.closeCase(caseId, new CaseClosureRequest("Second closure", true));

        verify(notificationService).sendFinalFeedbackNotification(second);
    }
```

The existing `closeCase_whenHealingNotContinued_...` test keeps passing unchanged (the mock returns `false` by default, so the request is sent).

- [ ] **Step 2: Run tests to verify they fail**

Run (from `cph/app`): `gradlew.bat test --tests "com.pranicdoc.cph.healing.service.CaseServiceTest"`
Expected: compilation FAIL — `existsByHealingSessionIdAndFeedbackType` is not defined.

- [ ] **Step 3: Implement**

In `QualitativeFeedbackRepository.java`, add below the existing `Optional` finder:

```java
    boolean existsByHealingSessionIdAndFeedbackType(UUID sessionId, FeedbackType feedbackType);
```

In `CaseService.java`, add the import `com.pranicdoc.cph.healing.repository.QualitativeFeedbackRepository` and `com.pranicdoc.cph.common.enums.FeedbackType` if not already present, add the field after `feedbackLinkRepository`:

```java
    private final QualitativeFeedbackRepository qualitativeFeedbackRepository;
```

and replace the end of `closeCase`:

```java
        caseRepository.save(healingCase);
        if(!request.isHealingContinued()) {
            notificationService.sendFinalFeedbackNotification(lastSession);
        }
```

with:

```java
        caseRepository.save(healingCase);
        // Every closure (continued or not) asks for final feedback, except a re-close with no new
        // session: that session already has its FINAL, so asking again would be a duplicate request.
        if (!qualitativeFeedbackRepository.existsByHealingSessionIdAndFeedbackType(
                lastSession.getId(), FeedbackType.FINAL)) {
            notificationService.sendFinalFeedbackNotification(lastSession);
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat test --tests "com.pranicdoc.cph.healing.service.CaseServiceTest" --tests "*CaseServiceOwnershipTest" --tests "*CaseControllerAuthorizationTest"`
Expected: PASS. (If `CaseServiceOwnershipTest` constructs `CaseService` with explicit mocks and fails to compile/inject, add the same `@Mock QualitativeFeedbackRepository` there.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pranicdoc/cph/healing/repository/QualitativeFeedbackRepository.java app/src/main/java/com/pranicdoc/cph/healing/service/impl/CaseService.java app/src/test/java/com/pranicdoc/cph/healing/service/
git commit -m "feat(feedback): request final feedback on continued-healing closures

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: FINAL feedback submission overwrites instead of throwing

**Files:**
- Modify: `app/src/main/java/com/pranicdoc/cph/healing/service/impl/FeedbackService.java:59-86`
- Create: `app/src/test/java/com/pranicdoc/cph/healing/service/FeedbackServiceTest.java`

**Interfaces:**
- Consumes: `QualitativeFeedbackRepository.findByHealingSession_HealingCase_IdAndFeedbackType(UUID, FeedbackType)` (existing, `Optional`), `SessionService.findLatestByCaseIdOrThrow(UUID)`, `FeedbackLinkRepository.markUsedBySessionId(UUID, LinkStatus, LinkStatus)`
- Produces: `saveQualitativeFeedback(UUID caseId, QualitativeFeedbackRequest request)` — same signature; FINAL now upserts.

- [ ] **Step 1: Write the failing tests**

Create `FeedbackServiceTest.java`:

```java
package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.healing.dto.QualitativeFeedbackRequest;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.model.QualitativeFeedback;
import com.pranicdoc.cph.healing.repository.QualitativeFeedbackRepository;
import com.pranicdoc.cph.healing.repository.SymptomFeedbackRepository;
import com.pranicdoc.cph.healing.service.impl.FeedbackService;
import com.pranicdoc.cph.healing.service.impl.SessionService;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock private SymptomFeedbackRepository symptomFeedbackRepository;
    @Mock private QualitativeFeedbackRepository qualitativeFeedbackRepository;
    @Mock private SessionService sessionService;
    @Mock private FeedbackLinkRepository feedbackLinkRepository;

    @InjectMocks private FeedbackService feedbackService;

    private HealingSession session(int number) {
        HealingSession s = HealingSession.builder().sessionNumber(number).build();
        s.setId(UUID.randomUUID());
        return s;
    }

    @Test
    void firstFinal_insertsNewRowAndMarksLinkUsed() {
        UUID caseId = UUID.randomUUID();
        HealingSession latest = session(1);
        when(sessionService.findLatestByCaseIdOrThrow(caseId)).thenReturn(latest);
        when(qualitativeFeedbackRepository
                .findByHealingSession_HealingCase_IdAndFeedbackType(caseId, FeedbackType.FINAL))
                .thenReturn(Optional.empty());

        feedbackService.saveQualitativeFeedback(caseId,
                new QualitativeFeedbackRequest(FeedbackType.FINAL, "Much better", "Thanks"));

        ArgumentCaptor<QualitativeFeedback> saved = ArgumentCaptor.forClass(QualitativeFeedback.class);
        verify(qualitativeFeedbackRepository).save(saved.capture());
        assertEquals(FeedbackType.FINAL, saved.getValue().getFeedbackType());
        assertEquals("Much better", saved.getValue().getFeedbackText());
        assertSame(latest, saved.getValue().getHealingSession());
        verify(feedbackLinkRepository).markUsedBySessionId(caseId, LinkStatus.USED, LinkStatus.ACTIVE);
    }

    @Test
    void secondFinal_overwritesTextRemarksAndRepointsToLatestSession() {
        UUID caseId = UUID.randomUUID();
        HealingSession oldSession = session(1);
        HealingSession latest = session(2);
        QualitativeFeedback existing = QualitativeFeedback.builder()
                .healingSession(oldSession)
                .feedbackType(FeedbackType.FINAL)
                .feedbackText("Old text")
                .patientRemarks("Old remarks")
                .build();
        when(sessionService.findLatestByCaseIdOrThrow(caseId)).thenReturn(latest);
        when(qualitativeFeedbackRepository
                .findByHealingSession_HealingCase_IdAndFeedbackType(caseId, FeedbackType.FINAL))
                .thenReturn(Optional.of(existing));

        assertDoesNotThrow(() -> feedbackService.saveQualitativeFeedback(caseId,
                new QualitativeFeedbackRequest(FeedbackType.FINAL, "New text", "New remarks")));

        assertEquals("New text", existing.getFeedbackText());
        assertEquals("New remarks", existing.getPatientRemarks());
        assertSame(latest, existing.getHealingSession(),
                "overwrite must re-point the row to the latest session so PDF healing number/date aren't stale");
        verify(qualitativeFeedbackRepository).save(existing);
        verify(qualitativeFeedbackRepository, never()).save(argThat(f -> f != existing));
        verify(feedbackLinkRepository).markUsedBySessionId(caseId, LinkStatus.USED, LinkStatus.ACTIVE);
    }

    @Test
    void interim_afterFinalExists_insertsWithoutTouchingFinalOrLink() {
        UUID caseId = UUID.randomUUID();
        HealingSession latest = session(2);
        when(sessionService.findLatestByCaseIdOrThrow(caseId)).thenReturn(latest);

        feedbackService.saveQualitativeFeedback(caseId,
                new QualitativeFeedbackRequest(FeedbackType.INTERIM, "Midway", null));

        ArgumentCaptor<QualitativeFeedback> saved = ArgumentCaptor.forClass(QualitativeFeedback.class);
        verify(qualitativeFeedbackRepository).save(saved.capture());
        assertEquals(FeedbackType.INTERIM, saved.getValue().getFeedbackType());
        verify(qualitativeFeedbackRepository, never())
                .findByHealingSession_HealingCase_IdAndFeedbackType(any(), any());
        verify(feedbackLinkRepository, never()).markUsedBySessionId(any(), any(), any());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat test --tests "com.pranicdoc.cph.healing.service.FeedbackServiceTest"`
Expected: `secondFinal_overwritesTextRemarksAndRepointsToLatestSession` FAILS with `FeedbackAlreadySubmittedException`. The other two pass.

- [ ] **Step 3: Write minimal implementation**

In `FeedbackService.saveQualitativeFeedback` replace the body with:

```java
    @Auditable(action = "SAVE_QUALITATIVE_FEEDBACK")
    public void saveQualitativeFeedback(UUID caseId, QualitativeFeedbackRequest request) {
        HealingSession session = sessionService.findLatestByCaseIdOrThrow(caseId);

        if (request.feedbackType() == FeedbackType.FINAL) {
            // One FINAL per case: a later submission (continued-healing re-close) overwrites it.
            // Re-point to the latest session so the PDF's healing number/date are not stale;
            // earlier text stays in Envers history.
            QualitativeFeedback feedback = qualitativeFeedbackRepository
                .findByHealingSession_HealingCase_IdAndFeedbackType(caseId, FeedbackType.FINAL)
                .orElseGet(() -> QualitativeFeedback.builder()
                        .feedbackType(FeedbackType.FINAL)
                        .build());
            feedback.setHealingSession(session);
            feedback.setFeedbackText(request.feedbackText());
            feedback.setPatientRemarks(request.patientRemarks());
            qualitativeFeedbackRepository.save(feedback);
            feedbackLinkRepository.markUsedBySessionId(caseId, LinkStatus.USED, LinkStatus.ACTIVE);
            return;
        }

        qualitativeFeedbackRepository.save(QualitativeFeedback.builder()
                .healingSession(session)
                .feedbackType(request.feedbackType())
                .feedbackText(request.feedbackText())
                .patientRemarks(request.patientRemarks())
                .build());
    }
```

`FeedbackAlreadySubmittedException` is still imported/used by `saveSymptomFeedback`, so leave the import.

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat test --tests "com.pranicdoc.cph.healing.service.FeedbackServiceTest" --tests "*FeedbackControllerAuthorizationContractTest"`
Expected: PASS.

- [ ] **Step 5: Full module test and commit**

Run: `gradlew.bat test`
Expected: PASS (the 7 pre-existing failures noted on main, protocol and organ-scan Postgres tests, may still fail; confirm they are the same set and nothing new).

```bash
git add app/src/main/java/com/pranicdoc/cph/healing/service/impl/FeedbackService.java app/src/test/java/com/pranicdoc/cph/healing/service/FeedbackServiceTest.java
git commit -m "feat(feedback): FINAL qualitative feedback overwrites instead of rejecting

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

## Self-Review

- **Spec coverage:** send on every closure (T1), skip when latest session has FINAL (T1), one-FINAL overwrite + re-point + remove FINAL throw (T2), INTERIM unchanged (T2), repository `exists…` method (T1), error cases need no code. Nothing uncovered.
- **Placeholders:** none.
- **Types:** `existsByHealingSessionIdAndFeedbackType(UUID, FeedbackType)` and the existing `findByHealingSession_HealingCase_IdAndFeedbackType` are used consistently across tasks.
