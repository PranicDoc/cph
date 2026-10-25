import { useState, useEffect } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import api from "../lib/axios";
import { useUiVersion } from "../ui-version";
import { Skeleton } from "../components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Send, CheckCircle2 } from "lucide-react";
import {
  persistIsFinalFlag,
  readIsFinalFlag,
} from "../lib/feedbackFlag";
import {
  SymptomRatingList,
  type FeedbackSymptom,
} from "../components/feedback/SymptomRatingList";
import { toSymptomRating } from "../lib/symptomRating";

export default function PatientFeedback() {
  const { isNewView } = useUiVersion();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const [sessionReady, setSessionReady] = useState(false);

  const token =
    searchParams.get("token") ?? sessionStorage.getItem("feedbackToken");

  const caseId =
    searchParams.get("caseId") ?? sessionStorage.getItem("feedbackCaseId");

  const isFinal = readIsFinalFlag(searchParams, sessionStorage);

  const [isInitializing, setIsInitializing] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const [availableSymptoms, setAvailableSymptoms] = useState<FeedbackSymptom[]>([]);
  // The healing being rated: the case's latest session, which the feedback is attached to.
  const [healingDate, setHealingDate] = useState<string | null>(null);
  const [symptomRatings, setSymptomRatings] = useState<Record<string, number>>(
    {}
  );
  const [feedbackText, setFeedbackText] = useState("");

  /**
   * Read token & caseId from URL once,
   * store them in sessionStorage,
   * then remove them from the URL.
   */
  useEffect(() => {
    const tokenFromUrl = searchParams.get("token");
    const caseIdFromUrl = searchParams.get("caseId");

    if (tokenFromUrl && caseIdFromUrl) {
      sessionStorage.setItem("feedbackToken", tokenFromUrl);
      sessionStorage.setItem("feedbackCaseId", caseIdFromUrl);
      persistIsFinalFlag(searchParams, sessionStorage);

      navigate("/feedback", {
        replace: true,
      });

      return;
    }

    setSessionReady(true);
  }, [searchParams, navigate]);

  useEffect(() => {
    if (!sessionReady) {
      return;
    }

    const initAndFetch = async () => {
      if (!token || !caseId) {
        navigate("/unauthorized");
        return;
      }

      try {
        const init = await api.post("/api/v1/auth/init-feedback-session", {
          token,
        });
        setHealingDate(init?.data?.healingDate ?? null);
      } catch (err) {
        navigate("/unauthorized", { state: { error: err } });
        return;
      }

      setIsInitializing(false);
      setIsLoading(true);

      try {
        const res = await api.get("/api/v1/cases/self-service/symptoms", {
          params: {
            caseId,
            page: 0,
            size: 100,
          },
        });

        setAvailableSymptoms(res.data.content ?? res.data);
      } catch (err) {
        console.error("Failed to fetch symptoms:", err);
      } finally {
        setIsLoading(false);
      }
    };

    initAndFetch();
  }, [token, caseId, navigate, sessionReady]);

  const handleSymptomRatingChange = (symptomId: string, rating: number) => {
    setSymptomRatings((prev) => ({
      ...prev,
      [symptomId]: rating,
    }));
  };

  const handleSubmit = async () => {
    if (Object.keys(symptomRatings).length === 0) {
      alert("Please rate at least one symptom.");
      return;
    }

    if (!caseId) return;

    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const symptomPayload = Object.entries(symptomRatings).map(
        ([caseSymptomId, severity]) => ({
          caseSymptomId,
          rating: toSymptomRating(severity),
          notes: "",
        })
      );

      await api.post(
        `/api/v1/sessions/${caseId}/feedback/symptoms`,
        symptomPayload
      );

      // The comment is kept for interim feedback too, like the healer's manual entry.
      if (feedbackText.trim()) {
        await api.post(`/api/v1/sessions/${caseId}/feedback/qualitative`, {
          feedbackType: isFinal ? "FINAL" : "INTERIM",
          feedbackText,
          patientRemarks: "",
        });
      }
      setIsSubmitted(true);
    } catch (err) {
      if (
        err?.response?.data?.type ===
        "https://pranicdoc.com/errors/feedback-already-submitted"
      ) {
        setIsSubmitted(true);
        return;
      }
      setSubmitError(
        "Something went wrong while submitting. Please try again."
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isInitializing) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="space-y-3 w-full max-w-2xl mx-auto px-4">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-4 w-64" />
          <Skeleton className="h-48 w-full rounded-xl" />
        </div>
      </div>
    );
  }

  if (isSubmitted) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen space-y-4 px-4">
        <CheckCircle2 className="w-16 h-16 text-green-500" />
        <h2 className="text-2xl font-bold text-primary-dark">Thank You!</h2>
        <p className="text-text-light text-center max-w-md">
          Your feedback has been successfully submitted. We appreciate you
          taking the time to update us on your condition.
        </p>
      </div>
    );
  }

  return (
    <div className={isNewView ? "hlx mx-auto max-w-3xl px-4 pb-10 pt-8" : "max-w-3xl mx-auto space-y-8 pb-10 pt-8 px-4"}>
      <div>
        <h1 className="text-2xl font-bold text-primary-dark">
          Healing Feedback
        </h1>
        <p className="text-text-light text-sm mt-1">
          Please rate the symptoms you are currently experiencing.
        </p>
        {healingDate && (
          <p className="text-sm text-text mt-2">
            Healing date:{" "}
            <span className="font-medium">
              {new Date(`${healingDate}T00:00:00`).toLocaleDateString()}
            </span>
          </p>
        )}
      </div>

      <div className="bg-card border border-border rounded-xl shadow-sm p-6 space-y-8">
        <section className="space-y-4">
          <div>
            <h2 className="text-lg font-semibold text-primary-dark">
              1. Current Symptoms
            </h2>
            <p className="text-sm text-text-light">
              Rate each symptom based on how severe it feels right now.
            </p>
          </div>

          <SymptomRatingList
            symptoms={availableSymptoms}
            ratings={symptomRatings}
            onChange={handleSymptomRatingChange}
            isNewView={isNewView}
            loading={isLoading}
          />
        </section>

        <hr className="border-border" />

        <section className="space-y-4">
          <div>
            <h2 className="text-lg font-semibold text-primary-dark">
              2. Additional Comments
            </h2>
            <p className="text-sm text-text-light">
              Is there anything else you'd like us to know? (Optional)
            </p>
          </div>

          <Textarea
            value={feedbackText}
            onChange={(e) => setFeedbackText(e.target.value)}
            placeholder="Type your feedback here..."
            className="min-h-[120px] p-3 border text-text resize-y"
          />
        </section>
      </div>

      {submitError && (
        <p className="text-sm text-red-500 text-right">{submitError}</p>
      )}

      <div className="flex justify-end">
        <Button
          onClick={handleSubmit}
          disabled={isSubmitting || isLoading}
          className="gap-2 px-6 py-2.5 h-auto"
        >
          {isSubmitting ? (
            "Submitting..."
          ) : (
            <>
              Submit Feedback
              <Send className="w-4 h-4" />
            </>
          )}
        </Button>
      </div>
    </div>
  );
}
