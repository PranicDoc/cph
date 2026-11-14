import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { Download } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { useReviewStream } from "../../hooks/useReviewStream";
import { reviewFileKey, reviewInboxItemKey } from "../../lib/reviewQueryKeys";
import PdfDocumentView from "./PdfDocumentView";
import { inboxFileUrl, inboxItem, MOVED_MESSAGE, NEW_VERSION_MESSAGE, STATUS_LABELS } from "./reviewApi";

/**
 * One submission for the assigned instructor: the PDF of one revision, a download button, and a
 * banner when a newer revision has arrived. The page never switches revision on its own; the
 * instructor opts in by clicking the banner, so a read in progress is not pulled away.
 */
export default function ReviewViewer() {
  const { submissionId = "" } = useParams<{ submissionId: string }>();
  // Live events refetch the submission below; the banner is derived from that refetch.
  useReviewStream();

  const [chosenRevision, setChosenRevision] = useState<number | null>(null);
  // The newest revision that existed when the page opened; shown until the reviewer picks another.
  const [openedAt, setOpenedAt] = useState<number | null>(null);

  const submission = useQuery({
    queryKey: reviewInboxItemKey(submissionId),
    queryFn: () => inboxItem(submissionId),
    enabled: !!submissionId,
    retry: false,
  });

  const latest = submission.data?.latestRevisionNo ?? 0;
  if (openedAt === null && latest > 0) setOpenedAt(latest);
  const displayed = chosenRevision ?? openedAt ?? 0;

  const file = useQuery({
    queryKey: reviewFileKey(submissionId, displayed),
    queryFn: () => inboxFileUrl(submissionId, displayed),
    enabled: !!submissionId && displayed > 0,
    staleTime: 5 * 60_000,
    retry: false,
  });

  const forbidden = [submission.error, file.error].some((e) => isAxiosError(e) && e.response?.status === 403);
  if (forbidden) {
    return (
      <div className="mx-auto w-full max-w-3xl space-y-3 p-4">
        <p role="alert" className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          {MOVED_MESSAGE}
        </p>
        <Link to="/reviews" className="text-sm underline">
          Back to reviews
        </Link>
      </div>
    );
  }

  const revisions = submission.data?.revisions ?? [];
  const hasNewer = displayed > 0 && latest > displayed;

  return (
    <div className="mx-auto w-full max-w-4xl space-y-3 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-3">
          <Link to="/reviews" className="text-sm underline">
            Back
          </Link>
          {submission.data && (
            <span className="text-sm text-muted-foreground">{STATUS_LABELS[submission.data.status]}</span>
          )}
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

      {hasNewer && (
        <button
          type="button"
          className="w-full rounded border border-blue-300 bg-blue-50 p-2 text-sm text-blue-900"
          onClick={() => setChosenRevision(latest)}
        >
          {NEW_VERSION_MESSAGE}
        </button>
      )}

      {(submission.isError || file.isError) && (
        <p role="alert" className="text-sm text-red-500">
          Could not load this submission.
        </p>
      )}
      {file.data && <PdfDocumentView key={file.data} fileUrl={file.data} />}
    </div>
  );
}
