import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "@/lib/axios";
import { extractDetail } from "@/lib/utils";
import { useUiVersion } from "@/ui-version";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { toSymptomRating } from "@/lib/symptomRating";
import { SymptomRatingList, type FeedbackSymptom } from "./SymptomRatingList";

type FeedbackType = "INTERIM" | "FINAL";

const ALREADY_SUBMITTED = "https://pranicdoc.com/errors/feedback-already-submitted";

/** Today as yyyy-mm-dd in the healer's own timezone (the date input's format). */
const today = () => new Date().toLocaleDateString("en-CA");

/**
 * Lets the healer record a patient's feedback by hand, as an alternative to the WhatsApp
 * link: the same symptom ratings and comments the patient's form collects, plus the date of
 * the healing the feedback is about (left empty for the healer to fill in).
 */
export function ManualFeedbackDialog({
  healingCaseId,
  patientName,
  onClose,
}: {
  healingCaseId: string;
  patientName: string;
  onClose: () => void;
}) {
  const { isNewView } = useUiVersion();
  const [feedbackType, setFeedbackType] = useState<FeedbackType>("INTERIM");
  const [healingDate, setHealingDate] = useState("");
  const [ratings, setRatings] = useState<Record<string, number>>({});
  const [feedbackText, setFeedbackText] = useState("");
  const [error, setError] = useState<string | null>(null);

  const symptomsQuery = useQuery<FeedbackSymptom[]>({
    queryKey: ["case-feedback-symptoms", healingCaseId],
    queryFn: () =>
      api
        .get("/api/v1/cases/symptoms", { params: { caseId: healingCaseId, page: 0, size: 100 } })
        .then((res) => res.data.content ?? res.data),
  });

  const save = useMutation({
    mutationFn: () =>
      api.post(`/api/v1/cases/${healingCaseId}/feedback`, {
        feedbackType,
        healingDate,
        symptoms: Object.entries(ratings).map(([caseSymptomId, value]) => ({
          caseSymptomId,
          rating: toSymptomRating(value, isNewView),
          notes: "",
        })),
        feedbackText,
      }),
    onSuccess: () => {
      toast.success("Feedback saved");
      onClose();
    },
    onError: (err: unknown) =>
      setError(
        (err as { response?: { data?: { type?: string } } })?.response?.data?.type === ALREADY_SUBMITTED
          ? "Feedback for this healing is already recorded."
          : extractDetail(err) || "Failed to save feedback, try again"
      ),
  });

  const submit = () => {
    setError(null);
    if (!healingDate) {
      setError("Enter the healing date.");
      return;
    }
    if (Object.keys(ratings).length === 0 && !feedbackText.trim()) {
      setError("Rate at least one symptom or add a comment.");
      return;
    }
    save.mutate();
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="w-full max-w-2xl max-h-[90vh] overflow-y-auto space-y-5 gap-0">
        <DialogTitle className="text-lg font-semibold text-primary-dark">
          Patient feedback — {patientName}
        </DialogTitle>

        <div className="flex flex-wrap items-end gap-4">
          <div className="space-y-1.5">
            <Label htmlFor="manual-feedback-date">Healing date</Label>
            <Input
              id="manual-feedback-date"
              type="date"
              value={healingDate}
              max={today()}
              onChange={(e) => setHealingDate(e.target.value)}
              className="w-44"
            />
          </div>
          <div className="space-y-1.5">
            <span className="text-sm font-medium">Feedback type</span>
            <div role="group" aria-label="Feedback type" className="flex rounded-lg border border-border p-0.5">
              {(["INTERIM", "FINAL"] as const).map((type) => (
                <Button
                  key={type}
                  type="button"
                  aria-pressed={feedbackType === type}
                  variant={feedbackType === type ? "secondary" : "ghost"}
                  onClick={() => setFeedbackType(type)}
                  className="px-3 py-1.5 h-auto text-sm"
                >
                  {type === "INTERIM" ? "Interim" : "Final"}
                </Button>
              ))}
            </div>
          </div>
        </div>

        <section className="space-y-3">
          <h3 className="font-semibold text-primary-dark">Symptoms</h3>
          <SymptomRatingList
            symptoms={symptomsQuery.data ?? []}
            ratings={ratings}
            onChange={(id, value) => setRatings((prev) => ({ ...prev, [id]: value }))}
            isNewView={isNewView}
            loading={symptomsQuery.isPending}
          />
        </section>

        <section className="space-y-2">
          <Label htmlFor="manual-feedback-text">Comments</Label>
          <Textarea
            id="manual-feedback-text"
            value={feedbackText}
            onChange={(e) => setFeedbackText(e.target.value)}
            placeholder="What the patient said (optional)"
            className="min-h-[100px] p-3"
          />
        </section>

        {error && <p className="text-sm text-red-500">{error}</p>}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="button" onClick={submit} disabled={save.isPending}>
            {save.isPending ? "Saving…" : "Save feedback"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
