import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { Download } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { useEventStream } from "../../hooks/useEventStream";
import { REVIEW_INBOX_KEY, reviewFileKey, reviewInboxItemKey } from "../../lib/reviewQueryKeys";
import PdfPinCanvas from "./PdfPinCanvas";
import ThreadPanel from "./ThreadPanel";
import { useReviewComments } from "./useReviewComments";
import { pinsForRevision, scrollToThread } from "./threads";
import {
  approve,
  inboxFileUrl,
  inboxItem,
  MOVED_MESSAGE,
  NEW_VERSION_MESSAGE,
  requestChanges,
  reviewErrorMessage,
  STATUS_LABELS,
} from "./reviewApi";

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

  const queryClient = useQueryClient();
  const [decisionError, setDecisionError] = useState<string | null>(null);
  const decision = useMutation({
    mutationFn: ({ kind, revisionNo }: { kind: "approve" | "request-changes"; revisionNo: number }) =>
      kind === "approve" ? approve(submissionId, revisionNo) : requestChanges(submissionId, revisionNo),
    onMutate: () => setDecisionError(null),
    onError: (e) => setDecisionError(reviewErrorMessage(e, "Could not save your decision. Try again.")),
    onSettled: () => {
      // A refusal usually means a newer version arrived: the refetch brings up the refresh banner.
      queryClient.invalidateQueries({ queryKey: reviewInboxItemKey(submissionId) });
      queryClient.invalidateQueries({ queryKey: REVIEW_INBOX_KEY });
    },
  });

  const thread = useReviewComments("reviewer", submissionId);
  const [placing, setPlacing] = useState(false);
  const [draft, setDraft] = useState<{ page: number; x: number; y: number } | null>(null);
  const [activeThread, setActiveThread] = useState<string | null>(null);

  const forbidden = [submission.error, file.error, thread.comments.error].some((e) => isAxiosError(e) && e.response?.status === 403);
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

  return (
    <div className="mx-auto w-full max-w-6xl space-y-3 p-4">
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
          <Button type="button" size="sm" variant="outline" disabled={!canDecide} onClick={() => decide("request-changes")}>
            Request changes
          </Button>
          <Button type="button" size="sm" disabled={!canDecide} onClick={() => decide("approve")}>
            Approve
          </Button>
        </div>
      </div>

      {decisionError && (
        <p role="alert" className="rounded border border-amber-300 bg-amber-50 p-2 text-sm text-amber-900">
          {decisionError}
        </p>
      )}

      {hasNewer && (
        <button
          type="button"
          className="w-full rounded border border-blue-300 bg-blue-50 p-2 text-sm text-blue-900"
          onClick={() => {
            setChosenRevision(latest);
            setDecisionError(null);
          }}
        >
          {NEW_VERSION_MESSAGE}
        </button>
      )}

      {(submission.isError || file.isError) && (
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
        </div>
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
            error={thread.error}
          />
        </div>
      </div>
    </div>
  );
}
