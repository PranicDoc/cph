import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft } from "lucide-react";
import api from "@/lib/axios";
import { extractDetail } from "@/lib/utils";
import { useUiVersion } from "@/ui-version";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { toSymptomRating } from "@/lib/symptomRating";
import { SymptomRatingList, type FeedbackSymptom } from "@/components/feedback/SymptomRatingList";

type FeedbackType = "INTERIM" | "FINAL";

const ALREADY_SUBMITTED = "https://pranicdoc.com/errors/feedback-already-submitted";

/** Today as yyyy-mm-dd in the healer's own timezone (the date input's format). */
const today = () => new Date().toLocaleDateString("en-CA");

/**
 * The healer records a patient's feedback by hand, as an alternative to the WhatsApp link:
 * the same symptom sliders and comment the patient's form collects, plus the date of the
 * healing the feedback is about (left empty for the healer to fill in). A full page rather
 * than a dialog, so it renders inside the new view's styles like every other page.
 */
export default function ManualFeedback() {
  const { isNewView } = useUiVersion();
  const navigate = useNavigate();
  const { healingCaseId = "" } = useParams<{ healingCaseId: string }>();
  const patientName = (useLocation().state as { patientName?: string } | null)?.patientName;

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
    enabled: !!healingCaseId,
  });

  const backToPatients = () => navigate("/patients");

  const save = useMutation({
    mutationFn: () =>
      api.post(`/api/v1/cases/${healingCaseId}/feedback`, {
        feedbackType,
        healingDate,
        symptoms: Object.entries(ratings).map(([caseSymptomId, severity]) => ({
          caseSymptomId,
          rating: toSymptomRating(severity),
          notes: "",
        })),
        feedbackText,
      }),
    onSuccess: () => {
      toast.success("Feedback saved");
      backToPatients();
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
    <div className={isNewView ? "hlx mx-auto max-w-3xl pb-10" : "max-w-3xl mx-auto space-y-6 pb-10"}>
      <div className="space-y-2">
        <Button type="button" variant="ghost" onClick={backToPatients} className="h-auto px-0 gap-1.5 text-sm">
          <ArrowLeft className="w-4 h-4" />
          Patients
        </Button>
        <h1 className="text-xl md:text-2xl font-bold text-primary-dark">
          Patient feedback{patientName ? ` — ${patientName}` : ""}
        </h1>
        <p className="text-sm text-text-light">
          Record what the patient reported, the same way the WhatsApp feedback form does.
        </p>
      </div>

      <div className="bg-card border border-border rounded-xl shadow-sm p-4 md:p-6 space-y-6 mt-4">
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
          <div>
            <h2 className="font-semibold text-primary-dark">Symptoms</h2>
            <p className="text-sm text-text-light">Severity right now: 0 is none, 10 is the worst.</p>
          </div>
          <SymptomRatingList
            symptoms={symptomsQuery.data ?? []}
            ratings={ratings}
            onChange={(id, severity) => setRatings((prev) => ({ ...prev, [id]: severity }))}
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
      </div>

      {error && <p className="text-sm text-red-500 text-right mt-3">{error}</p>}

      <div className="flex justify-end gap-2 mt-4">
        <Button type="button" variant="outline" onClick={backToPatients}>
          Cancel
        </Button>
        <Button type="button" onClick={submit} disabled={save.isPending}>
          {save.isPending ? "Saving…" : "Save feedback"}
        </Button>
      </div>
    </div>
  );
}
