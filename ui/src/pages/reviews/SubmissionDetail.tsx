import { useState } from "react";
import { isAxiosError } from "axios";
import { Download } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useEventStream } from "../../hooks/useEventStream";
import { useReviewComments } from "../../hooks/useReviewComments";
import { useReviewFileUrl } from "../../hooks/useReviewFileUrl";
import { useHealerSubmission } from "../../hooks/useReviewSubmission";
import PdfPinCanvas from "./PdfPinCanvas";
import { ReviewStatusBadge } from "./ReviewStatusBadge";
import RevisionPicker from "./RevisionPicker";
import { SubmitForReviewButton } from "./SubmitForReviewButton";
import ThreadPanel from "./ThreadPanel";
import { pinsForRevision, scrollToThread } from "./threads";
import { formatWhen, HEAD_BADGE, PAGE, SURFACE, WARN_BOX } from "./ui";

/**
 * The healer's own submission, read-only but for replies and resubmitting: the PDF with the instructor's pins, and the threads with a
 * reply box. No pin placement, no resolve, no approve: those belong to the instructor.
 */
export default function SubmissionDetail() {
  const { submissionId = "" } = useParams<{ submissionId: string }>();
  useEventStream();

  const [chosenRevision, setChosenRevision] = useState<number | null>(null);
  const [activeThread, setActiveThread] = useState<string | null>(null);

  const sub = useHealerSubmission(submissionId);
  const thread = useReviewComments("healer", submissionId);

  const latest = sub.data?.latestRevisionNo ?? 0;
  const displayed = chosenRevision ?? latest;

  const file = useReviewFileUrl("healer", submissionId, displayed);

  const denied = [sub.error, file.error, thread.comments.error].some(
    (e) => isAxiosError(e) && (e.response?.status === 403 || e.response?.status === 404)
  );
  if (denied) {
    return (
      <div className={PAGE}>
        <p role="alert" className={WARN_BOX}>
          This submission is not available.
        </p>
        <Link to="/submissions" className="ongr text-sm underline">
          Back to submissions
        </Link>
      </div>
    );
  }

  const revisions = sub.data?.revisions ?? [];
  const comments = thread.comments.data ?? [];
  const pins = pinsForRevision(comments, displayed);
  const selectThread = (id: string) => {
    setActiveThread(id);
    scrollToThread(id);
  };
  const submission = sub.data;
  const status = submission?.status;
  const canResubmit = status === "CHANGES_REQUESTED" || status === "DISMISSED";

  return (
    <div className={PAGE}>
      <div className="page-head flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <Link to="/submissions" className="ongr text-sm underline">
            Back
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold">Submission</h1>
            {status && <ReviewStatusBadge className={HEAD_BADGE} status={status} />}
          </div>
          {submission && (
            <p className="muted mt-1 text-sm text-muted-foreground">Last updated {formatWhen(submission.updatedAt)}</p>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <RevisionPicker revisions={revisions} latestRevisionNo={latest} value={displayed} onChange={setChosenRevision} />
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

      {status === "CHANGES_REQUESTED" && (
        <div role="status" className={WARN_BOX}>
          <b>Your instructor asked for changes.</b> Read the pinned comments, edit the case, then generate a new PDF and
          resubmit.
        </div>
      )}

      {(sub.isError || file.isError) && (
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
          />
        </div>
      </div>

      {submission && canResubmit && (
        <div className={cn(SURFACE, "flex flex-wrap items-center justify-end gap-3 p-3")}>
          <p className="muted min-w-[16rem] flex-1 text-sm text-muted-foreground">
            {status === "CHANGES_REQUESTED" &&
              `If nothing changed since R${latest}, resubmitting asks your instructor to look again without creating a new revision.`}
          </p>
          <Button asChild size="sm" variant="outline">
            <Link to={`/session/${submission.caseId}`}>Edit case</Link>
          </Button>
          <SubmitForReviewButton caseId={submission.caseId} status={submission.status} variant="default" />
        </div>
      )}
    </div>
  );
}
