import { useEffect, useState } from "react";
import { SingleSelect } from "@/components/ui/single-select";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft } from "lucide-react";
import api from "@/lib/axios";
import { extractDetail } from "@/lib/utils";
import { useUiVersion } from "@/ui-version";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { fromSymptomRating, toSymptomRating } from "@/lib/symptomRating";
import { TEXT_LIMITS } from "@/lib/textLimits";
import { CharacterCount } from "@/components/form/CharacterCount";
import { SymptomRatingList, type FeedbackSymptom } from "@/components/feedback/SymptomRatingList";

type FeedbackType = "INTERIM" | "FINAL";

interface CaseSession {
  id: string;
  sessionNumber: number | null;
  sessionDate: string | null;
}

/** GET /api/v1/cases/{id}/feedback: what is already recorded, shown again for editing. */
interface RecordedFeedback {
  interims: {
    sessionId: string;
    feedbackText: string | null;
    ratings: { caseSymptomId: string; rating: number }[];
  }[];
  finalFeedbackText: string | null;
  finalFeedbackDate?: string | null;
}

const ALREADY_SUBMITTED = "https://pranicdoc.com/errors/feedback-already-submitted";

/**
 * The healer records or edits a patient's feedback by hand, as an alternative to the WhatsApp link:
 * the same symptom sliders and comment the patient's form collects. Interim feedback is one per
 * session, for the session the healer picks (its date is shown for reference, not editable); final
 * feedback is one comment per case, kept on the latest session, with a date the healer can set. Picking one that is already recorded
 * opens it for editing. A full page rather than a dialog, so it renders inside the new view's styles
 * like every other page.
 */
export default function ManualFeedback() {
  const { isNewView } = useUiVersion();
  const navigate = useNavigate();
  const { healingCaseId = "" } = useParams<{ healingCaseId: string }>();
  const patientName = (useLocation().state as { patientName?: string } | null)?.patientName;

  const [feedbackType, setFeedbackType] = useState<FeedbackType>("INTERIM");
  const [sessionId, setSessionId] = useState("");
  const [ratings, setRatings] = useState<Record<string, number>>({});
  const [feedbackText, setFeedbackText] = useState("");
  const [chosenDate, setChosenDate] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const symptomsQuery = useQuery<FeedbackSymptom[]>({
    queryKey: ["case-feedback-symptoms", healingCaseId],
    queryFn: () =>
      api
        .get("/api/v1/cases/symptoms", { params: { caseId: healingCaseId, page: 0, size: 100 } })
        .then((res) => res.data.content ?? res.data),
    enabled: !!healingCaseId,
  });

  const sessionsQuery = useQuery<CaseSession[]>({
    queryKey: ["case-sessions", healingCaseId],
    queryFn: () => api.get(`/api/v1/sessions/case/${healingCaseId}`).then((res) => res.data),
    enabled: !!healingCaseId,
  });
  const sessions = Array.isArray(sessionsQuery.data) ? sessionsQuery.data : [];

  const queryClient = useQueryClient();
  const recordedQuery = useQuery<RecordedFeedback>({
    queryKey: ["case-feedback", healingCaseId],
    queryFn: () => api.get(`/api/v1/cases/${healingCaseId}/feedback`).then((res) => res.data),
    enabled: !!healingCaseId,
  });
  const recorded = recordedQuery.data;

  const isFinal = feedbackType === "FINAL";
  const recordedInterim = recorded?.interims?.find((i) => i.sessionId === sessionId);
  const isEditing = isFinal ? !!recorded?.finalFeedbackText : !!recordedInterim;
  const textLimit = isFinal ? TEXT_LIMITS.finalFeedback : TEXT_LIMITS.interimFeedback;

  // Picking a session or the final feedback shows what is recorded for it, ready to edit.
  useEffect(() => {
    if (!recorded) return;
    setChosenDate(null);
    if (isFinal) {
      setFeedbackText(recorded.finalFeedbackText ?? "");
      setRatings({});
    } else {
      const interim = recorded.interims?.find((i) => i.sessionId === sessionId);
      setFeedbackText(interim?.feedbackText ?? "");
      setRatings(
        Object.fromEntries(
          (interim?.ratings ?? []).map((r) => [r.caseSymptomId, fromSymptomRating(r.rating)])
        )
      );
    }
    setError(null);
  }, [recorded, isFinal, sessionId]);

  const latestSession = sessions.reduce<CaseSession | null>(
    (latest, s) => (!latest || (s.sessionNumber ?? 0) > (latest.sessionNumber ?? 0) ? s : latest),
    null
  );
  // An interim shows its session's date (read-only). A final's date is the healer's pick, else what is
  // recorded, else the latest session's.
  const shownDate = isFinal
    ? chosenDate ?? recorded?.finalFeedbackDate ?? latestSession?.sessionDate ?? ""
    : sessions.find((s) => s.id === sessionId)?.sessionDate ?? "";

  const backToPatients = () => navigate("/patients");

  const save = useMutation({
    mutationFn: () =>
      api.post(`/api/v1/cases/${healingCaseId}/feedback`, {
        feedbackType,
        ...(!isFinal && sessionId ? { sessionId } : {}),
        symptoms: isFinal ? [] : Object.entries(ratings).map(([caseSymptomId, severity]) => ({
          caseSymptomId,
          rating: toSymptomRating(severity),
          notes: "",
        })),
        feedbackText,
        ...(isFinal && shownDate ? { finalFeedbackDate: shownDate } : {}),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["case-feedback", healingCaseId] });
      toast.success(isEditing ? "Feedback updated" : "Feedback saved");
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
    if (feedbackText.trim().length > textLimit) {
      setError(`Shorten the comment to ${textLimit} characters.`);
      return;
    }
    if (isFinal) {
      if (!feedbackText.trim()) {
        setError("Add the final feedback comment.");
        return;
      }
    } else if (!sessionId) {
      setError("Pick the session this feedback is for.");
      return;
    } else if (Object.keys(ratings).length === 0 && !feedbackText.trim()) {
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
          Record what the patient reported, the same way the WhatsApp feedback form does. One interim
          feedback per session and one final feedback per case; saved feedback opens here to edit.
        </p>
      </div>

      <div className="bg-card border border-border rounded-xl shadow-sm p-4 md:p-6 space-y-6 mt-4">
        <div className="flex flex-wrap items-end gap-4">
          {!isFinal && (
            <div className="space-y-1.5">
              <Label htmlFor="manual-feedback-session">Session</Label>
              <SingleSelect
                id="manual-feedback-session"
                value={sessionId}
                onChange={setSessionId}
                placeholder="Select a session"
                options={sessions.map((s) => ({
                  value: s.id,
                  label: `Session ${s.sessionNumber ?? "?"}${s.sessionDate ? ` — ${s.sessionDate}` : ""}`,
                }))}
                className="flex h-9 w-56 items-center justify-between gap-2 rounded-md border border-border bg-card px-2 text-sm"
              />
            </div>
          )}
          <div className="space-y-1.5">
            <Label htmlFor="manual-feedback-date">Healing date</Label>
            <Input
              id="manual-feedback-date"
              type="date"
              value={shownDate}
              max={new Date().toLocaleDateString("en-CA")}
              onChange={(e) => setChosenDate(e.target.value)}
              disabled={!isFinal}
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

        {!isFinal && (
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
        )}

        <section className="space-y-2">
          <Label htmlFor="manual-feedback-text">Comments</Label>
          <Textarea
            id="manual-feedback-text"
            value={feedbackText}
            onChange={(e) => setFeedbackText(e.target.value)}
            maxLength={textLimit}
            className="min-h-[100px] p-3"
          />
          <CharacterCount value={feedbackText} max={textLimit} />
        </section>
      </div>

      {error && <p className="text-sm text-red-500 text-right mt-3">{error}</p>}

      <div className="flex justify-end gap-2 mt-4">
        <Button type="button" variant="outline" onClick={backToPatients}>
          Cancel
        </Button>
        <Button type="button" onClick={submit} disabled={save.isPending}>
          {save.isPending ? "Saving…" : isEditing ? "Update feedback" : "Save feedback"}
        </Button>
      </div>
    </div>
  );
}
