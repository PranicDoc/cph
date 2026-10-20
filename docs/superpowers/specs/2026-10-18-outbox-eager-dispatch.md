# Outbox eager dispatch + sweeper (replaces the 2 s poller)

**Status:** agreed 2026-10-18 · **Repo:** cph · **Branch:** `feature/outbox-eager-dispatch`

## Why
`NotificationScheduler` polls `notification_outbox` every 2 s. On scale-to-zero Cloud Run (UAT) a timer
only runs while some request happens to be in flight, so WhatsApp messages go out late or never. On the VM
(prod) it is 43k pointless queries a day. It also has defects:
- `OutboxRepository` is typed `JpaRepository<NotificationOutbox, Long>`, but the id is a `UUID`.
- Rows are marked PROCESSING inside a transaction and handed to `@Async` threads that can run before that
  commit.
- A row left in PROCESSING (instance died mid-send) is never picked up again.

## Design
1. **Write**: `NotificationServiceImpl` saves the outbox row (unchanged) and publishes `OutboxCreatedEvent(id)`.
2. **Eager dispatch**: `OutboxDispatchListener` handles the event `@TransactionalEventListener(AFTER_COMMIT)`.
   - `app.notifications.dispatch=async` (VM/prod): it hands off to `whatsappWorkerExecutor`, so the user
     doesn't wait.
   - `app.notifications.dispatch=sync` (Cloud Run): it sends inline, in the same request. Cloud Run freezes
     the CPU once the response is returned, so async work can't be relied on.
   - Failures never propagate to the caller. The sweeper is the safety net.
3. **Claim** (`OutboxDispatcher`): a single conditional UPDATE moves a row to PROCESSING, but only if the
   row is PENDING, FAILED, or PROCESSING and stale. Whoever updates 1 row sends; everyone else skips. This
   makes the eager path and the sweeper safe to race. Reclaiming a stale PROCESSING row counts as an attempt.
4. **Send**: `WhatsappSender` makes the HTTP call with no transaction open. The result is recorded in a
   short transaction: success marks the row PROCESSED; failure increments `retry_count`, then sets FAILED,
   or DLQ once `retry_count >= max-retries`.
5. **Sweep** (`OutboxSweeper`), every 30 min:
   - First marks DLQ any rows that are stale-PROCESSING and out of retries.
   - Then dispatches, synchronously: PENDING rows older than `pending-grace`, FAILED rows with retries
     left, and stale-PROCESSING rows with retries left.
   - VM: triggered by `@Scheduled(cron)` (`app.notifications.sweep.scheduler-enabled=true`).
   - Cloud Run: triggered by Cloud Scheduler calling `POST /cph/api/v1/internal/notifications/sweep` with an
     `X-Internal-Key` header. `/api/v1/internal/**` has its own security filter chain
     (`InternalApiSecurityConfig`, ordered first), where `InternalApiKeyFilter` checks the key against
     `app.internal.api-key`. It answers 404 when the key is unset and 401 when the key is wrong.
6. **Publish is best-effort**: if publishing the event throws, the error is logged and swallowed. The
   caller's transaction still commits the row, and the sweeper sends it.
7. **HTTP**: WhatsApp calls go through `common.http.RestTemplateService`. It offers get/post/put/delete
   overloads (URL, body, headers, `HttpAuth` none/bearer/basic/apiKey) on top of a single `exchange`
   method.

Delivery is at-least-once: a crash after WhatsApp accepts a message but before the DB write means it is
sent again.

## Config (defaults)
```yaml
app:
  notifications:
    dispatch: async            # async | sync
    max-retries: 3
    pending-grace: 2m
    stuck-after: 10m
    sweep:
      scheduler-enabled: true
      cron: "0 */30 * * * *"
      batch-size: 100
  internal:
    api-key: ${INTERNAL_API_KEY:}
```
Cloud Run UAT overrides: `APP_NOTIFICATIONS_DISPATCH=sync`, `APP_NOTIFICATIONS_SWEEP_SCHEDULER_ENABLED=false`.

## Out of scope
- A separate notification service; we revisit when SMS, email or reminders arrive.
- pdfWorker on Cloud Run.
