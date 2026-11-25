import { useState } from "react";
import { isAxiosError } from "axios";
import { Download } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { useAuth } from "../../context/AuthContext";
import { useEventStream } from "../../hooks/useEventStream";
import { useReviewComments } from "../../hooks/useReviewComments";
import { useReviewDecision } from "../../hooks/useReviewDecision";
import { useReviewFileUrl } from "../../hooks/useReviewFileUrl";
import { useReviewerSubmission } from "../../hooks/useReviewSubmission";
import { cn, extractDetail } from "../../lib/utils";
import PdfPinCanvas from "./PdfPinCanvas";
import { ReviewStatusBadge } from "./ReviewStatusBadge";
import RevisionPicker from "./RevisionPicker";
import SuperAdminBanner from "./SuperAdminBanner";
import ThreadPanel from "./ThreadPanel";
import { pinsForRevision, scrollToThread } from "./threads";
import { formatWhen, HEAD_BADGE, PAGE, SURFACE, WARN_BOX } from "./ui";

/** Shown in the viewer when the instructor loses the submission (reassigned) while it is open. */
const MOVED_MESSAGE = "This submission was moved to another instructor";
/** The refresh banner shown when a newer revision arrives while one is open. */
const NEW_VERSION_MESSAGE = "New version available — refresh";

/**
 * One submission for the assigned instructor: the PDF of one revision, a download button, and a
 * banner when a newer revision has arrived. The page never switches revision on its own; the
 * instructor opts in by clicking the banner, so a read in progress is not pulled away. Approve and
 * Request changes act on the revision on screen and are offered only when it is the latest; if a
 * newer one reached the server first, its refusal is shown and the submission refetched.
 */
export default function ReviewViewer() {
  const { submissionId = "" } = useParams<{ submissionId: string }>();
  // Live events refetch the submission below; the banner is derived from that refetch.
  useEventStream();

  const [chosenRevision, setChosenRevision] = useState<number | null>(null);
  // The newest revision that existed when the page opened; shown until the reviewer picks another.
  const [openedAt, setOpenedAt] = useState<number | null>(null);

  const submission = useReviewerSubmission(submissionId);
  const { user } = useAuth();

  const latest = submission.data?.latestRevisionNo ?? 0;
  if (openedAt === null && latest > 0) setOpenedAt(latest);
  const displayed = chosenRevision ?? openedAt ?? 0;

  const file = useReviewFileUrl("reviewer", submissionId, displayed);

  const decision = useReviewDecision(submissionId);
  // A stale decision (409) stays on screen as a banner; every other failure is toasted by the hook.
  const decisionError =
    isAxiosError(decision.error) && decision.error.response?.status === 409
      ? extractDetail(decision.error) || "Could not save your decision. Try again."
      : null;

  const thread = useReviewComments("reviewer", submissionId);
  const [placing, setPlacing] = useState(false);
  const [draft, setDraft] = useState<{ page: number; x: number; y: number } | null>(null);
  const [activeThread, setActiveThread] = useState<string | null>(null);

  const forbidden = [submission.error, file.error, thread.comments.error].some((e) => isAxiosError(e) && e.response?.status === 403);
  if (forbidden) {
    return (
      <div className={PAGE}>
        <p role="alert" className={WARN_BOX}>
          {MOVED_MESSAGE}
        </p>
        <Link to="/reviews" className="ongr text-sm underline">
          Back to reviews
        </Link>
      </div>
    );
  }

  const revisions = submission.data?.revisions ?? [];
  const hasNewer = displayed > 0 && latest > displayed;
  const comments = thread.comments.data ?? [];
  // New pins go on the latest version only; older versions keep the pins they already have.
  const canPin = displayed > 0 && displayed === latest;
  const status = submission.data?.status;
  const canDecide = canPin && (status === "PENDING_REVIEW" || status === "CHANGES_REQUESTED") && !decision.isPending;
  const decide = (kind: "approve" | "request-changes") => decision.mutate({ kind, revisionNo: displayed });
  const pins = [
    ...pinsForRevision(comments, displayed),
    ...(draft ? [{ id: "draft", page: draft.page, x: draft.x, y: draft.y, label: "+", draft: true }] : []),
  ];
  const selectThread = (id: string) => {
    setActiveThread(id);
    scrollToThread(id);
  };
  // A super admin may be here without being the assigned reviewer, including when there is none.
  const actingAsSuperAdmin =
    user?.role === "SUPADM" && !!submission.data && submission.data.reviewerId !== user.id;
  const healerName = submission.data?.healerName;
  const submittedAt = revisions.find((r) => r.revisionNo === latest)?.submittedAt ?? null;

  return (
    <div className={PAGE}>
      <div className="page-head flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <Link to="/reviews" className="ongr text-sm underline">
            Back
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="break-words text-2xl font-semibold">{submission.data?.caseTitle ?? "Review"}</h1>
            {status && (
              <ReviewStatusBadge className={HEAD_BADGE} status={status} label={status === "DISMISSED" ? "Dismissed" : undefined} />
            )}
          </div>
          {submittedAt && (
            <p className="muted mt-1 text-sm text-muted-foreground">
              {healerName ? `${healerName}, submitted ` : "Submitted "}
              {formatWhen(submittedAt)}
            </p>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <RevisionPicker revisions={revisions} latestRevisionNo={latest} value={displayed} onChange={setChosenRevision} />
          <Button
            type="button"
            size="sm"
            variant={placing ? "default" : "outline"}
            aria-pressed={placing}
            disabled={!file.data || !canPin}
            onClick={() => setPlacing((v) => !v)}
          >
            {placing ? "Click the PDF to place a pin" : "Add pin"}
          </Button>
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={!file.data}
            onClick={() => file.data && window.open(file.data, "_blank", "noopener")}
          >
            <Download className="h-3.5 w-3.5" />
            Download
          </Button>
        </div>
      </div>

      {actingAsSuperAdmin && <SuperAdminBanner />}

      {decisionError && (
        <p role="alert" className={WARN_BOX}>
          {decisionError}
        </p>
      )}

      {hasNewer && (
        <button
          type="button"
          className="w-full rounded-xl border border-primary bg-primary/10 p-3 text-sm font-medium"
          onClick={() => {
            setChosenRevision(latest);
            decision.reset();
          }}
        >
          {NEW_VERSION_MESSAGE}
        </button>
      )}

      {(submission.isError || file.isError) && (
        <p role="alert" className="ongr text-sm text-destructive">
          Could not load this submission.
        </p>
      )}
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
        <section aria-label={`Case sheet PDF, revision ${displayed}`} className={cn(SURFACE, "min-w-0 self-start p-2")}>
          {file.data && (
            <PdfPinCanvas
              key={file.data}
              fileUrl={file.data}
              pins={pins}
              activePinId={activeThread}
              onSelectPin={selectThread}
              onAddPin={
                placing && canPin
                  ? (page, x, y) => {
                      setDraft({ page, x, y });
                      setPlacing(false);
                    }
                  : undefined
              }
            />
          )}
        </section>
        <div className="lg:sticky lg:top-4 lg:max-h-[85vh] lg:self-start lg:overflow-y-auto">
          <ThreadPanel
            comments={comments}
            latestRevisionNo={latest}
            activeThreadId={activeThread}
            onSelectThread={selectThread}
            onReply={(parentId, body) => thread.add({ parentId, body })}
            onResolve={thread.resolve}
            onAddGeneral={displayed > 0 ? (body) => thread.add({ revisionNo: displayed, body }) : undefined}
            draftPin={draft}
            onSubmitDraft={async (body) => {
              if (!draft) return;
              await thread.add({ revisionNo: displayed, page: draft.page, x: draft.x, y: draft.y, body });
              setDraft(null);
            }}
            onCancelDraft={() => setDraft(null)}
          />
        </div>
      </div>

      <div className={cn(SURFACE, "flex flex-wrap items-center justify-end gap-3 p-3")}>
        <p className="muted min-w-[16rem] flex-1 text-sm text-muted-foreground">
          Approving locks this revision. Any later edit to the case sets the approval aside and asks the healer to submit
          again.
        </p>
        <Button
          type="button"
          variant="outline"
          className="text-[color:var(--warn,var(--color-amber-900))]"
          disabled={!canDecide}
          onClick={() => decide("request-changes")}
        >
          Request changes
        </Button>
        <Button type="button" disabled={!canDecide} onClick={() => decide("approve")}>
          Approve
        </Button>
      </div>
    </div>
  );
}
