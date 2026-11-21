import { useState } from "react";
import { isAxiosError } from "axios";
import { Download } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { useEventStream } from "../../hooks/useEventStream";
import { useReviewComments } from "../../hooks/useReviewComments";
import { useReviewFileUrl } from "../../hooks/useReviewFileUrl";
import { useHealerSubmission } from "../../hooks/useReviewSubmission";
import { STATUS_LABELS } from "../../types/review";
import PdfPinCanvas from "./PdfPinCanvas";
import ThreadPanel from "./ThreadPanel";
import { pinsForRevision, scrollToThread } from "./threads";

/**
 * The healer's own submission, read-only: the PDF with the instructor's pins, and the threads with a
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
      <div className="mx-auto w-full max-w-3xl space-y-3 p-4">
        <p role="alert" className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          This submission is not available.
        </p>
        <Link to="/submissions" className="text-sm underline">
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

  return (
    <div className="mx-auto w-full max-w-6xl space-y-3 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-3">
          <Link to="/submissions" className="text-sm underline">
            Back
          </Link>
          {sub.data && <span className="text-sm text-muted-foreground">{STATUS_LABELS[sub.data.status]}</span>}
        </div>
        <div className="flex items-center gap-2">
          {revisions.length > 1 && (
            <label className="flex items-center gap-2 text-sm">
              Version
              <select
                className="rounded border bg-background px-2 py-1"
                value={displayed}
                onChange={(e) => setChosenRevision(Number(e.target.value))}
              >
                {revisions.map((r) => (
                  <option key={r.revisionNo} value={r.revisionNo}>
                    {r.revisionNo}
                  </option>
                ))}
              </select>
            </label>
          )}
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

      {(sub.isError || file.isError) && (
        <p role="alert" className="text-sm text-red-500">
          Could not load this submission.
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="min-w-0">
          {file.data && (
            <PdfPinCanvas
              key={file.data}
              fileUrl={file.data}
              pins={pins}
              activePinId={activeThread}
              onSelectPin={selectThread}
            />
          )}
        </div>
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
    </div>
  );
}
