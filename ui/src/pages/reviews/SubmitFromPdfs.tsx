import { useState } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { Send } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import api from "../../lib/axios";
import { usePatient } from "../../context/PatientContext";
import { useCaseReviewStatuses } from "../../hooks/useCaseReviewStatuses";
import { ReviewStatusBadge } from "./ReviewStatusBadge";
import { SubmitForReviewButton } from "./SubmitForReviewButton";

interface CasePdf {
  healingCaseId: string;
  completedAt: string;
}

interface PdfPage {
  content?: CasePdf[];
  positionAt?: string | null;
}

async function fetchPdfPage(cursor: string | null): Promise<PdfPage> {
  const params = new URLSearchParams({ limit: "50" });
  if (cursor) params.append("scrollPosition", cursor);
  const response = await api.get(`/api/v1/cases/pdfs?${params.toString()}`);
  return response.data.data;
}

/** The healer's case PDFs that can still be sent (everything but approved), each with its Submit button. */
function SubmittablePdfList() {
  const { patients } = usePatient();
  const pdfs = useInfiniteQuery({
    queryKey: ["submittable-pdfs"],
    queryFn: ({ pageParam }) => fetchPdfPage(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.content?.length ? (last.positionAt ?? null) : null),
    // Opening the list again must show PDFs generated since last time.
    staleTime: 0,
  });
  const jobs = pdfs.data?.pages.flatMap((p) => p.content ?? []) ?? [];
  const statuses = useCaseReviewStatuses(jobs.map((j) => j.healingCaseId), { scope: "submit-from-pdfs" });
  const ready = jobs.flatMap((job) => {
    const review = statuses.data?.[job.healingCaseId];
    return review?.eligible && review.status !== "APPROVED" ? [{ job, review }] : [];
  });

  if (pdfs.isError || statuses.isError) {
    return <p role="alert" className="text-sm text-destructive">Could not load your case PDFs.</p>;
  }
  if (pdfs.isLoading || statuses.isLoading) {
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }

  return (
    <div className="space-y-3">
      {ready.length === 0 && !pdfs.hasNextPage && (
        <p className="text-sm text-muted-foreground">
          No case PDF is waiting to be sent. Generate a case PDF first, and it will be listed here.
        </p>
      )}
      <ul className="space-y-2">
        {ready.map(({ job, review }) => (
          <li
            key={job.healingCaseId}
            className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border p-3"
          >
            <div className="min-w-0">
              <div className="break-words text-sm font-semibold">
                {patients.find((p) => p.healingCaseId === job.healingCaseId)?.fullName || "Unknown Patient"}
              </div>
              <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                PDF made {new Date(job.completedAt).toLocaleDateString()}
                <ReviewStatusBadge status={review.status} />
              </div>
            </div>
            <SubmitForReviewButton caseId={job.healingCaseId} status={review.status} />
          </li>
        ))}
      </ul>
      {pdfs.hasNextPage && (
        <Button type="button" variant="outline" size="sm" disabled={pdfs.isFetchingNextPage} onClick={() => pdfs.fetchNextPage()}>
          {pdfs.isFetchingNextPage ? "Loading…" : "Show older PDFs"}
        </Button>
      )}
    </div>
  );
}

/** "Submit a case for review": opens the list of case PDFs, to send one from there. */
export function SubmitFromPdfs() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button type="button" onClick={() => setOpen(true)}>
        <Send className="h-4 w-4" />
        Submit a case for review
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="flex max-h-[80vh] flex-col overflow-hidden sm:max-w-lg">
          <DialogTitle>Submit a case for review</DialogTitle>
          <DialogDescription>Choose a case PDF to send to your instructor.</DialogDescription>
          <div className="overflow-y-auto">{open && <SubmittablePdfList />}</div>
        </DialogContent>
      </Dialog>
    </>
  );
}
